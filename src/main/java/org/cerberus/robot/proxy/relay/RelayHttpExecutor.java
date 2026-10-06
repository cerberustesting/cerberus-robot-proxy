package org.cerberus.robot.proxy.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLContext;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;

/**
 * Runs one fully built HTTP request from this machine: manual redirects, per-call TLS
 * verification, response decompression, size limits, global timeout and a guard refusing the
 * proxy's own local services. Port of the cerberus-local-runner http-executor.js.
 */
final class RelayHttpExecutor {

    private static final int MAX_REDIRECTS = 10;
    private static final Set<String> HOP_BY_HOP = new HashSet<String>(Arrays.asList(
            "connection", "keep-alive", "transfer-encoding", "proxy-authenticate", "proxy-connection", "upgrade"));

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "relay-watchdog");
            t.setDaemon(true);
            return t;
        }
    });

    private RelayHttpExecutor() {
    }

    interface UrlCheck {

        /** @return a reason to refuse the URL, or null to allow it */
        String refuse(URI target);
    }

    static final class Request {

        String method;
        URI uri;
        List<String[]> headers = new ArrayList<String[]>();
        byte[] body;
        boolean followRedirects = true;
    }

    static final class Options {

        long timeoutMs;
        boolean acceptUnsignedSsl;
        int maxBodyBytes;
        Set<Integer> blockedLocalPorts = new HashSet<Integer>();
        UrlCheck urlCheck;
    }

    static final class Result {

        int status;
        String statusText;
        List<String[]> headers;
        byte[] body;
        boolean truncated;
        String finalUrl;
    }

    /** Raised by the socket factories when a connection to a protected local service is attempted. */
    static final class BlockedTargetException extends IOException {

        BlockedTargetException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- execution

    static Result execute(Request request, Options options) throws RelayException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.timeoutMs);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        String method = request.method;
        URI target = request.uri;
        byte[] body = request.body;
        List<String[]> headers = new ArrayList<String[]>(request.headers);
        // Computed by the client from the URL / the body; a stale value would corrupt the request.
        removeHeader(headers, "content-length");
        removeHeader(headers, "host");
        removeHeader(headers, "transfer-encoding");
        boolean callerUserAgent = hasHeader(headers, "user-agent");

        try (CloseableHttpClient client = buildClient(options, callerUserAgent)) {
            for (int hop = 0;; hop++) {
                String refused = options.urlCheck == null ? null : options.urlCheck.refuse(target);
                if (refused != null) {
                    throw new RelayException(403, "target_blocked", refused);
                }
                Result result = requestOnce(client, method, target, headers, body, options, deadline, timedOut);
                String location = firstHeader(result.headers, "location");
                boolean redirect = location != null && (result.status == 301 || result.status == 302
                        || result.status == 303 || result.status == 307 || result.status == 308);
                if (redirect && request.followRedirects) {
                    if (hop >= MAX_REDIRECTS) {
                        throw new RelayException(502, "connect_failed", "Too many redirects");
                    }
                    URI next = resolve(target, location);
                    if (next == null || next.getHost() == null) {
                        throw new RelayException(502, "connect_failed", "Invalid redirect location");
                    }
                    if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                        throw new RelayException(403, "target_blocked", "Redirect to unsupported protocol: " + next.getScheme());
                    }
                    // Same rules as browsers: 303 (and 301/302 after a POST) become GET without body.
                    if (result.status == 303 || ((result.status == 301 || result.status == 302) && "POST".equals(method))) {
                        if (!"HEAD".equals(method)) {
                            method = "GET";
                        }
                        body = null;
                        removeHeader(headers, "content-length");
                        removeHeader(headers, "content-type");
                    }
                    if (!origin(next, true).equals(origin(target, true))) {
                        removeHeader(headers, "authorization");
                        removeHeader(headers, "cookie");
                        removeHeader(headers, "proxy-authorization");
                    }
                    target = next;
                    continue;
                }
                result.finalUrl = target.toString();
                return result;
            }
        } catch (RelayException e) {
            throw e;
        } catch (IOException e) {
            throw translate(e, timedOut, options);
        } catch (RuntimeException e) {
            throw translate(e, timedOut, options);
        } catch (java.security.GeneralSecurityException e) {
            throw translate(e, timedOut, options);
        }
    }

    private static RelayException translate(Exception e, AtomicBoolean timedOut, Options options) {
        String timeoutMessage = "Timed out after " + options.timeoutMs / 1000.0 + "s";
        if (timedOut.get()) {
            return new RelayException(504, "timeout", timeoutMessage);
        }
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof BlockedTargetException) {
                return new RelayException(403, "target_blocked", t.getMessage());
            }
        }
        if (e instanceof SocketTimeoutException || e instanceof org.apache.http.conn.ConnectTimeoutException) {
            return new RelayException(504, "timeout", timeoutMessage);
        }
        return new RelayException(502, "connect_failed",
                "Call failed: " + e.getMessage() + " (" + e.getClass().getSimpleName() + ")");
    }

    private static Result requestOnce(CloseableHttpClient client, String method, URI target, List<String[]> headers,
            byte[] body, Options options, long deadline, AtomicBoolean timedOut) throws IOException, RelayException {
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remainingMs <= 0) {
            timedOut.set(true);
            throw new RelayException(504, "timeout", "Timed out after " + options.timeoutMs / 1000.0 + "s");
        }
        final HttpRequestBase request;
        boolean enclosing = body != null || "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        if (enclosing) {
            request = new EnclosingRequest(method);
            ((EnclosingRequest) request).setEntity(new ByteArrayEntity(body == null ? new byte[0] : body));
        } else {
            request = new PlainRequest(method);
        }
        request.setURI(target);
        for (String[] h : headers) {
            request.addHeader(h[0], h[1]);
        }
        int ms = (int) Math.min(Integer.MAX_VALUE, remainingMs);
        request.setConfig(RequestConfig.custom().setConnectTimeout(ms).setSocketTimeout(ms)
                .setConnectionRequestTimeout(ms).setRedirectsEnabled(false).build());

        // Socket timeouts only bound each read: the watchdog enforces the global deadline.
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                timedOut.set(true);
                request.abort();
            }
        }, remainingMs, TimeUnit.MILLISECONDS);
        try (CloseableHttpResponse response = client.execute(request)) {
            return read(response, request, method, options);
        } finally {
            watchdog.cancel(false);
        }
    }

    private static Result read(CloseableHttpResponse response, HttpRequestBase request, String method, Options options)
            throws IOException {
        int status = response.getStatusLine().getStatusCode();
        Result result = new Result();
        result.status = status;
        result.statusText = response.getStatusLine().getReasonPhrase() == null ? "" : response.getStatusLine().getReasonPhrase();
        result.body = new byte[0];

        List<String[]> all = new ArrayList<String[]>();
        for (Header h : response.getAllHeaders()) {
            all.add(new String[]{h.getName(), h.getValue()});
        }
        String encoding = firstHeader(all, "content-encoding");
        encoding = encoding == null ? "" : encoding.trim().toLowerCase(Locale.ROOT);
        boolean noBody = "HEAD".equals(method) || status == 204 || status == 304;
        boolean decoded = false;

        HttpEntity entity = response.getEntity();
        if (entity != null && !noBody) {
            InputStream in = entity.getContent();
            if (in != null) {
                PushbackInputStream peek = new PushbackInputStream(in, 2);
                if (!encoding.isEmpty() && (encoding.equals("gzip") || encoding.equals("x-gzip") || encoding.equals("deflate"))) {
                    decoded = true;
                    int first = peek.read();
                    if (first == -1) {
                        in = null; // empty body: nothing to decode
                    } else {
                        peek.unread(first);
                        in = encoding.equals("deflate") ? inflater(peek) : new GZIPInputStream(peek);
                    }
                } else {
                    in = peek;
                }
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (total + n > options.maxBodyBytes) {
                            out.write(buffer, 0, options.maxBodyBytes - total);
                            result.truncated = true;
                            break;
                        }
                        out.write(buffer, 0, n);
                        total += n;
                    }
                    result.body = out.toByteArray();
                    if (result.truncated) {
                        request.abort(); // do not read (or drain) more than the cap
                    }
                }
            }
        }

        result.headers = new ArrayList<String[]>();
        for (String[] h : all) {
            String lower = h[0].toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower)) {
                continue; // describes this connection, not the relayed body
            }
            if (decoded && (lower.equals("content-encoding") || lower.equals("content-length"))) {
                continue;
            }
            result.headers.add(h);
        }
        return result;
    }

    /** HTTP "deflate" is zlib-wrapped, but some servers send a raw deflate stream. */
    private static InputStream inflater(PushbackInputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b1 != -1) {
            in.unread(b1);
        }
        in.unread(b0);
        boolean zlib = b1 != -1 && (b0 & 0x0f) == 8 && (((b0 << 8) | b1) % 31) == 0;
        return new InflaterInputStream(in, new Inflater(!zlib));
    }

    // ------------------------------------------------------------------ client

    private static CloseableHttpClient buildClient(Options options, boolean callerUserAgent) throws java.security.GeneralSecurityException {
        PlainConnectionSocketFactory plain = new GuardedPlainSocketFactory(options.blockedLocalPorts);
        SSLConnectionSocketFactory ssl;
        if (options.acceptUnsignedSsl) {
            // This call only: trust everything, no hostname check.
            SSLContext context = SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();
            ssl = new GuardedSslSocketFactory(context, NoopHostnameVerifier.INSTANCE, options.blockedLocalPorts);
        } else {
            ssl = new GuardedSslSocketFactory(SSLContexts.createSystemDefault(),
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier(), options.blockedLocalPorts);
        }
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plain).register("https", ssl).build();
        org.apache.http.impl.client.HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(new BasicHttpClientConnectionManager(registry))
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .disableContentCompression(); // decoded by hand, and no Accept-Encoding injected
        if (!callerUserAgent) {
            builder.addInterceptorLast(new org.apache.http.HttpRequestInterceptor() {
                @Override
                public void process(org.apache.http.HttpRequest request, HttpContext context) {
                    request.removeHeaders("User-Agent");
                }
            });
        }
        return builder.build();
    }

    private static void guard(InetSocketAddress remote, Set<Integer> blockedPorts) throws IOException {
        InetAddress address = remote.getAddress();
        if (address != null && blockedPorts.contains(remote.getPort()) && isLoopbackOrUnspecified(address)) {
            throw new BlockedTargetException("Refusing to reach the local relay host's own service on port " + remote.getPort());
        }
    }

    static boolean isLoopbackOrUnspecified(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] b = address.getAddress();
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && b[10] == (byte) 0xff && b[11] == (byte) 0xff) {
                return b[12] == 127 || (b[12] == 0 && b[13] == 0 && b[14] == 0 && b[15] == 0);
            }
        }
        return false;
    }

    /** Checked on every address actually dialed, literal IPs and DNS results alike. */
    private static final class GuardedPlainSocketFactory extends PlainConnectionSocketFactory {

        private final Set<Integer> blockedPorts;

        GuardedPlainSocketFactory(Set<Integer> blockedPorts) {
            this.blockedPorts = blockedPorts;
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                InetSocketAddress localAddress, HttpContext context) throws IOException {
            guard(remoteAddress, blockedPorts);
            return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
        }
    }

    private static final class GuardedSslSocketFactory extends SSLConnectionSocketFactory {

        private final Set<Integer> blockedPorts;

        GuardedSslSocketFactory(SSLContext context, javax.net.ssl.HostnameVerifier verifier, Set<Integer> blockedPorts) {
            super(context, verifier);
            this.blockedPorts = blockedPorts;
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                InetSocketAddress localAddress, HttpContext context) throws IOException {
            guard(remoteAddress, blockedPorts);
            return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
        }
    }

    private static final class PlainRequest extends HttpRequestBase {

        private final String method;

        PlainRequest(String method) {
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    private static final class EnclosingRequest extends HttpEntityEnclosingRequestBase {

        private final String method;

        EnclosingRequest(String method) {
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    // ----------------------------------------------------------------- helpers

    static String origin(URI uri, boolean withPort) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1 && withPort) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        boolean defaultPort = ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (port == -1 || (!withPort && defaultPort) ? "" : ":" + port);
    }

    /** Lenient parse: characters browsers accept but java.net.URI rejects are percent-encoded. */
    static URI parseUri(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return stripFragment(new URI(s));
        } catch (URISyntaxException first) {
            StringBuilder sb = new StringBuilder();
            for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                int c = b & 0xff;
                if (c <= 0x20 || c >= 0x7f || "\"<>\\^`{|}".indexOf(c) >= 0) {
                    sb.append(String.format("%%%02X", c));
                } else {
                    sb.append((char) c);
                }
            }
            try {
                return stripFragment(new URI(sb.toString()));
            } catch (URISyntaxException second) {
                return null;
            }
        }
    }

    private static URI stripFragment(URI uri) throws URISyntaxException {
        if (uri.getRawFragment() == null) {
            return uri;
        }
        String s = uri.toString();
        return new URI(s.substring(0, s.indexOf('#')));
    }

    private static URI resolve(URI base, String location) {
        URI relative = parseUri(location);
        return relative == null ? null : base.resolve(relative);
    }

    private static String firstHeader(List<String[]> headers, String lowerName) {
        for (String[] h : headers) {
            if (h[0].toLowerCase(Locale.ROOT).equals(lowerName)) {
                return h[1];
            }
        }
        return null;
    }

    private static boolean hasHeader(List<String[]> headers, String lowerName) {
        return firstHeader(headers, lowerName) != null;
    }

    private static void removeHeader(List<String[]> headers, String lowerName) {
        for (Iterator<String[]> it = headers.iterator(); it.hasNext();) {
            if (it.next()[0].toLowerCase(Locale.ROOT).equals(lowerName)) {
                it.remove();
            }
        }
    }
}
