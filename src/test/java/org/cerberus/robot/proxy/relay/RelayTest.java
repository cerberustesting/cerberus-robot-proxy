package org.cerberus.robot.proxy.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayTest {

    private static final String TOKEN = "t0ken-for-tests";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIB10 = 10 * 1024 * 1024;

    private static HttpServer http;
    private static HttpServer http2;
    private static HttpsServer https;
    private static int port;
    private static int port2;
    private static int tlsPort;

    @BeforeAll
    static void startTargets() throws Exception {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        http2 = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        port = http.getAddress().getPort();
        port2 = http2.getAddress().getPort();

        http.createContext("/echo", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                List<String> multi = ex.getRequestHeaders().get("X-Multi");
                String text = ex.getRequestMethod() + "|" + new String(read(ex.getRequestBody()), StandardCharsets.UTF_8)
                        + "|" + multi + "|" + ex.getRequestHeaders().getFirst("Authorization")
                        + "|" + ex.getRequestHeaders().getFirst("Content-Type") + "|" + ex.getRequestURI().getRawQuery();
                ex.getResponseHeaders().add("Set-Cookie", "a=1");
                ex.getResponseHeaders().add("Set-Cookie", "b=2");
                ex.getResponseHeaders().add("Content-Type", "text/plain");
                reply(ex, 201, text.getBytes(StandardCharsets.UTF_8));
            }
        });
        http.createContext("/error", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                reply(ex, 503, "down".getBytes(StandardCharsets.UTF_8));
            }
        });
        http.createContext("/gzip", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                GZIPOutputStream gz = new GZIPOutputStream(bos);
                gz.write("hello gzip".getBytes(StandardCharsets.UTF_8));
                gz.close();
                ex.getResponseHeaders().add("Content-Encoding", "gzip");
                reply(ex, 200, bos.toByteArray());
            }
        });
        http.createContext("/redirect", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                // Read the body first: the JDK 11 HttpServer drops the connection of an exchange answered with an unread request body.
                read(ex.getRequestBody());
                ex.getResponseHeaders().add("Location", "/echo?x=1");
                reply(ex, 302, new byte[0]);
            }
        });
        http.createContext("/redirect-other-origin", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port2 + "/echo");
                reply(ex, 307, new byte[0]);
            }
        });
        http.createContext("/redirect-other-origin-cookie", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port2 + "/credentials");
                reply(ex, 307, new byte[0]);
            }
        });
        http.createContext("/redirect-same-origin-cookie", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.getResponseHeaders().add("Location", "/credentials");
                reply(ex, 307, new byte[0]);
            }
        });
        http.createContext("/credentials", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                reply(ex, 200, credentials(ex));
            }
        });
        http.createContext("/redirect-localhost", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.getResponseHeaders().add("Location", "http://localhost:" + port + "/echo");
                reply(ex, 302, new byte[0]);
            }
        });
        http.createContext("/redirect-ftp", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.getResponseHeaders().add("Location", "ftp://example.com/x");
                reply(ex, 302, new byte[0]);
            }
        });
        http.createContext("/big", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                byte[] big = new byte[MIB10 + 4096];
                java.util.Arrays.fill(big, (byte) 'x');
                reply(ex, 200, big);
            }
        });
        http.createContext("/slow", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                try {
                    reply(ex, 200, "late".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // client already gone
                }
            }
        });
        http.setExecutor(Executors.newCachedThreadPool());
        http.start();
        http2.createContext("/echo", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                reply(ex, 200, ("auth=" + ex.getRequestHeaders().getFirst("Authorization")).getBytes(StandardCharsets.UTF_8));
            }
        });
        http2.createContext("/credentials", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                reply(ex, 200, credentials(ex));
            }
        });
        http2.start();

        File dir = File.createTempFile("relay-tls", "");
        dir.delete();
        dir.mkdirs();
        File keystore = new File(dir, "ks.p12");
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/keytool", "-genkeypair", "-alias", "t",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "san=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", keystore.getPath(),
                "-storepass", "changeit", "-keypass", "changeit").redirectErrorStream(true).start();
        read(p.getInputStream());
        assertEquals(0, p.waitFor(), "keytool failed");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        InputStream in = new FileInputStream(keystore);
        ks.load(in, "changeit".toCharArray());
        in.close();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);
        https = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(ssl));
        https.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                reply(ex, 200, "secure".getBytes(StandardCharsets.UTF_8));
            }
        });
        https.start();
        tlsPort = https.getAddress().getPort();
    }

    @AfterAll
    static void stopTargets() {
        http.stop(0);
        http2.stop(0);
        https.stop(0);
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) {
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }

    private static void reply(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
        ex.close();
    }

    private static RelayService service(String token, String allowed, String blocked, int maxResponse, int maxRequest, int inFlight) {
        return new RelayService(token, allowed, blocked, 8093, maxResponse, maxRequest, inFlight);
    }

    private static RelayService service() {
        return service(TOKEN, "", "", MIB10, 20 * 1024 * 1024, 50);
    }

    private static MockMvc mvc(RelayService service) {
        return MockMvcBuilders.standaloneSetup(new RelayController(service)).build();
    }

    private static ObjectNode req(String method, String url) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("method", method);
        n.put("url", url);
        return n;
    }

    private static MockHttpServletResponse post(MockMvc mvc, String token, ObjectNode body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/relay")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsBytes(body))).andReturn().getResponse();
    }

    private static JsonNode json(MockHttpServletResponse r) throws Exception {
        return MAPPER.readTree(r.getContentAsByteArray());
    }

    private static String body(JsonNode out) {
        return new String(Base64.getDecoder().decode(out.get("bodyBase64").asText()), StandardCharsets.UTF_8);
    }

    private static void assertError(MockHttpServletResponse r, int status, String code) throws Exception {
        assertEquals(status, r.getStatus(), r.getContentAsString());
        assertEquals(code, json(r).get("code").asText());
    }

    private static byte[] credentials(HttpExchange ex) {
        return ("cookie=" + ex.getRequestHeaders().getFirst("Cookie")
                + "|proxyAuth=" + ex.getRequestHeaders().getFirst("Proxy-Authorization")
                + "|apiKey=" + ex.getRequestHeaders().getFirst("X-Api-Key")).getBytes(StandardCharsets.UTF_8);
    }

    private static ObjectNode withCredentials(String path) {
        ObjectNode r = req("GET", target(path));
        r.putObject("headers").put("Cookie", "sid=1").put("Proxy-Authorization", "Basic abc").put("X-Api-Key", "k");
        return r;
    }

    private static String target(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    // -------------------------------------------------------------------- tests

    @Test
    void authAndCheck() throws Exception {
        MockMvc mvc = mvc(service());
        assertError(mvc.perform(MockMvcRequestBuilders.get("/relay/check")).andReturn().getResponse(), 401, "unauthorized");
        assertError(mvc.perform(MockMvcRequestBuilders.get("/relay/check").header("Authorization", "Bearer nope")).andReturn().getResponse(), 401, "unauthorized");
        assertError(mvc.perform(MockMvcRequestBuilders.get("/relay/check").header("Authorization", TOKEN)).andReturn().getResponse(), 401, "unauthorized");
        assertError(post(mvc, "nope", req("GET", target("/echo"))), 401, "unauthorized");
        MockHttpServletResponse ok = mvc.perform(MockMvcRequestBuilders.get("/relay/check").header("Authorization", "Bearer " + TOKEN)).andReturn().getResponse();
        assertEquals(200, ok.getStatus());
        assertTrue(json(ok).get("ok").asBoolean());
        assertEquals(1, json(ok).get("version").asInt());
    }

    @Test
    void emptyTokenDisablesEverything() throws Exception {
        MockMvc mvc = mvc(service("", "", "", MIB10, 1000, 50));
        assertError(mvc.perform(MockMvcRequestBuilders.get("/relay/check").header("Authorization", "Bearer ")).andReturn().getResponse(), 503, "relay_disabled");
        assertError(mvc.perform(MockMvcRequestBuilders.get("/relay/check")).andReturn().getResponse(), 503, "relay_disabled");
        assertError(post(mvc, "", req("GET", target("/echo"))), 503, "relay_disabled");
    }

    @Test
    void relaysPostWithBodyDuplicateHeadersAndSetCookies() throws Exception {
        ObjectNode r = req("POST", target("/echo?q=1"));
        ObjectNode headers = r.putObject("headers");
        headers.putArray("X-Multi").add("one").add("two");
        headers.put("Content-Type", "text/plain");
        headers.put("Content-Length", "9999"); // stale value must be ignored
        r.put("bodyBase64", Base64.getEncoder().encodeToString("payload".getBytes(StandardCharsets.UTF_8)));
        JsonNode out = json(post(mvc(service()), TOKEN, r));
        assertEquals(201, out.get("status").asInt());
        assertEquals("POST|payload|[one, two]|null|text/plain|q=1", body(out));
        assertFalse(out.get("truncated").asBoolean());
        assertTrue(out.get("durationMs").asLong() >= 0);
        assertEquals(target("/echo?q=1"), out.get("finalUrl").asText());
        List<String> cookies = new ArrayList<String>();
        for (JsonNode h : (ArrayNode) out.get("headers")) {
            assertFalse(h.get(0).asText().equalsIgnoreCase("transfer-encoding"));
            assertFalse(h.get(0).asText().equalsIgnoreCase("connection"));
            if (h.get(0).asText().equalsIgnoreCase("set-cookie")) {
                cookies.add(h.get(1).asText());
            }
        }
        Collections.sort(cookies);
        assertEquals(java.util.Arrays.asList("a=1", "b=2"), cookies);
    }

    @Test
    void targetErrorStatusIsStillA200() throws Exception {
        MockHttpServletResponse r = post(mvc(service()), TOKEN, req("GET", target("/error")));
        assertEquals(200, r.getStatus());
        assertEquals(503, json(r).get("status").asInt());
        assertEquals("down", body(json(r)));
    }

    @Test
    void gzipBodyIsDecompressed() throws Exception {
        JsonNode out = json(post(mvc(service()), TOKEN, req("GET", target("/gzip"))));
        assertEquals("hello gzip", body(out));
        for (JsonNode h : (ArrayNode) out.get("headers")) {
            assertFalse(h.get(0).asText().equalsIgnoreCase("content-encoding"));
            assertFalse(h.get(0).asText().equalsIgnoreCase("content-length"));
        }
    }

    @Test
    void postRedirectBecomesGetWithoutBody() throws Exception {
        ObjectNode r = req("POST", target("/redirect"));
        r.putObject("headers").put("Content-Type", "text/plain");
        r.put("bodyBase64", Base64.getEncoder().encodeToString("payload".getBytes(StandardCharsets.UTF_8)));
        JsonNode out = json(post(mvc(service()), TOKEN, r));
        assertEquals("GET||null|null|null|x=1", body(out));
        assertEquals(target("/echo?x=1"), out.get("finalUrl").asText());
    }

    @Test
    void followRedirectsFalseReturnsTheRedirect() throws Exception {
        ObjectNode r = req("GET", target("/redirect"));
        r.put("followRedirects", false);
        JsonNode out = json(post(mvc(service()), TOKEN, r));
        assertEquals(302, out.get("status").asInt());
        assertEquals(target("/redirect"), out.get("finalUrl").asText());
    }

    @Test
    void authorizationIsDroppedOnCrossOriginRedirect() throws Exception {
        ObjectNode r = req("GET", target("/redirect-other-origin"));
        r.putObject("headers").put("Authorization", "Bearer secret");
        assertEquals("auth=null", body(json(post(mvc(service()), TOKEN, r))));
    }

    @Test
    void cookieAndProxyAuthorizationAreDroppedOnCrossOriginRedirect() throws Exception {
        ObjectNode r = withCredentials("/redirect-other-origin-cookie");
        assertEquals("cookie=null|proxyAuth=null|apiKey=k", body(json(post(mvc(service()), TOKEN, r))));
    }

    @Test
    void cookieAndProxyAuthorizationAreKeptOnSameOriginRedirect() throws Exception {
        ObjectNode r = withCredentials("/redirect-same-origin-cookie");
        assertEquals("cookie=sid=1|proxyAuth=Basic abc|apiKey=k", body(json(post(mvc(service()), TOKEN, r))));
    }

    @Test
    void redirectToNonHttpProtocolIsBlocked() throws Exception {
        assertError(post(mvc(service()), TOKEN, req("GET", target("/redirect-ftp"))), 403, "target_blocked");
    }

    @Test
    void invalidRequests() throws Exception {
        MockMvc mvc = mvc(service());
        assertError(post(mvc, TOKEN, req("TRACE", target("/echo"))), 400, "invalid_request");
        assertError(post(mvc, TOKEN, req("GET", "ftp://example.com/")), 400, "invalid_request");
        assertError(post(mvc, TOKEN, req("GET", "not a url")), 400, "invalid_request");
        ObjectNode bad = req("GET", target("/echo"));
        bad.putObject("headers").put("X-Evil", "a\r\nInjected: 1");
        assertError(post(mvc, TOKEN, bad), 400, "invalid_request");
        MockHttpServletResponse notJson = mvc.perform(MockMvcRequestBuilders.post("/relay")
                .header("Authorization", "Bearer " + TOKEN).content("{oops")).andReturn().getResponse();
        assertError(notJson, 400, "invalid_request");
    }

    @Test
    void selfSignedCertificateRefusedThenAccepted() throws Exception {
        MockMvc mvc = mvc(service());
        ObjectNode r = req("GET", "https://127.0.0.1:" + tlsPort + "/");
        assertError(post(mvc, TOKEN, r), 502, "connect_failed");
        r.put("acceptUnsignedSsl", true);
        JsonNode out = json(post(mvc, TOKEN, r));
        assertEquals(200, out.get("status").asInt());
        assertEquals("secure", body(out));
        // the relaxed trust must not leak into the next call
        r.put("acceptUnsignedSsl", false);
        assertError(post(mvc, TOKEN, r), 502, "connect_failed");
    }

    @Test
    void timeoutGives504() throws Exception {
        ObjectNode r = req("GET", target("/slow"));
        r.put("timeoutMs", 1000);
        long start = System.currentTimeMillis();
        assertError(post(mvc(service()), TOKEN, r), 504, "timeout");
        assertTrue(System.currentTimeMillis() - start < 2800);
    }

    @Test
    void connectionRefusedGives502() throws Exception {
        int free;
        ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        free = s.getLocalPort();
        s.close();
        MockHttpServletResponse r = post(mvc(service()), TOKEN, req("GET", "http://127.0.0.1:" + free + "/"));
        assertError(r, 502, "connect_failed");
        assertTrue(json(r).get("error").asText().length() > 10);
    }

    @Test
    void responseIsTruncatedAtTheCap() throws Exception {
        JsonNode out = json(post(mvc(service()), TOKEN, req("GET", target("/big"))));
        assertTrue(out.get("truncated").asBoolean());
        assertEquals(MIB10, Base64.getDecoder().decode(out.get("bodyBase64").asText()).length);

        MockMvc small = mvc(service(TOKEN, "", "", 5, 20 * 1024 * 1024, 50));
        JsonNode out2 = json(post(small, TOKEN, req("GET", target("/echo"))));
        assertTrue(out2.get("truncated").asBoolean());
        assertEquals(5, Base64.getDecoder().decode(out2.get("bodyBase64").asText()).length);
    }

    @Test
    void ownPortIsBlockedEvenViaLocalhostOrZeroAddress() throws Exception {
        MockMvc mvc = mvc(service(TOKEN, "", String.valueOf(port), MIB10, 20 * 1024 * 1024, 50));
        assertError(post(mvc, TOKEN, req("GET", target("/echo"))), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", "http://localhost:" + port + "/echo")), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", "http://0.0.0.0:" + port + "/echo")), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", "http://[::1]:" + port + "/echo")), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", "http://[::ffff:127.0.0.1]:" + port + "/echo")), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", target("/redirect-localhost"))), 403, "target_blocked");
        // another local API stays reachable
        assertEquals(200, json(post(mvc, TOKEN, req("GET", "http://127.0.0.1:" + port2 + "/echo"))).get("status").asInt());
    }

    @Test
    void serverPortIsAlwaysBlocked() throws Exception {
        RelayService svc = new RelayService(TOKEN, "", "", port, MIB10, 20 * 1024 * 1024, 50);
        assertError(post(mvc(svc), TOKEN, req("GET", target("/echo"))), 403, "target_blocked");
    }

    @Test
    void allowListAppliesToInitialUrlAndRedirects() throws Exception {
        MockMvc mvc = mvc(service(TOKEN, "127.0.0.*, *.example.com", "", MIB10, 20 * 1024 * 1024, 50));
        assertEquals(201, json(post(mvc, TOKEN, req("GET", target("/echo")))).get("status").asInt());
        assertError(post(mvc, TOKEN, req("GET", "http://localhost:" + port + "/echo")), 403, "target_blocked");
        assertError(post(mvc, TOKEN, req("GET", target("/redirect-localhost"))), 403, "target_blocked");
    }

    @Test
    void requestTooLargeGives413() throws Exception {
        MockMvc mvc = mvc(service(TOKEN, "", "", MIB10, 1000, 50));
        ObjectNode r = req("POST", target("/echo"));
        r.put("bodyBase64", Base64.getEncoder().encodeToString(new byte[2000]));
        assertError(post(mvc, TOKEN, r), 413, "request_too_large");
    }

    @Test
    void tooManyConcurrentCallsGive429() throws Exception {
        final MockMvc mvc = mvc(service(TOKEN, "", "", MIB10, 20 * 1024 * 1024, 1));
        final CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ObjectNode slow = req("GET", target("/slow"));
                    slow.put("timeoutMs", 5000);
                    post(mvc, TOKEN, slow);
                } catch (Exception ignored) {
                    // not under test
                } finally {
                    done.countDown();
                }
            }
        });
        t.start();
        Thread.sleep(700);
        assertError(post(mvc, TOKEN, req("GET", target("/echo"))), 429, "too_many_requests");
        done.await();
        assertEquals(201, json(post(mvc, TOKEN, req("GET", target("/echo")))).get("status").asInt());
    }

    @Test
    void secretsNeverReachTheLogs() throws Exception {
        final List<String> logged = Collections.synchronizedList(new ArrayList<String>());
        Configurator.setLevel("org.cerberus.robot.proxy.relay", Level.INFO);
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        AbstractAppender appender = new AbstractAppender("relay-test-capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                logged.add(event.getMessage().getFormattedMessage()
                        + (event.getThrown() == null ? "" : " " + event.getThrown()));
            }
        };
        appender.start();
        config.getRootLogger().addAppender(appender, Level.INFO, null);
        ctx.updateLoggers();
        try {
            RelayService svc = service();
            MockMvc mvc = mvc(svc);
            ObjectNode r = req("GET", target("/echo?apikey=QUERY-SECRET"));
            r.putObject("headers").put("X-Api-Key", "HEADER-SECRET");
            post(mvc, TOKEN, r);
            post(mvc, "WRONG-TOKEN-VALUE", r);
            ObjectNode fail = req("GET", "http://127.0.0.1:1/p?apikey=QUERY-SECRET");
            fail.putObject("headers").put("X-Api-Key", "HEADER-SECRET");
            post(mvc, TOKEN, fail);
        } finally {
            config.getRootLogger().removeAppender("relay-test-capture");
            appender.stop();
            ctx.updateLoggers();
        }
        String all = logged.toString();
        assertTrue(all.contains("/echo"), all);
        for (String secret : new String[]{TOKEN, "QUERY-SECRET", "HEADER-SECRET", "WRONG-TOKEN-VALUE", "Bearer"}) {
            assertFalse(all.contains(secret), "log leaks " + secret + ": " + all);
        }
    }
}
