package org.cerberus.robot.proxy.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validates a relay request, applies the security policy (token, host allowlist, local port
 * guard, concurrency and size limits) and delegates the call to {@link RelayHttpExecutor}.
 * The relay is disabled unless relay.token is set.
 */
@Service
public class RelayService {

    public static final int RELAY_VERSION = 1;

    private static final Logger LOG = LogManager.getLogger(RelayService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> METHODS = new HashSet<String>(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
    private static final long DEFAULT_TIMEOUT_MS = 60000L;
    private static final long MIN_TIMEOUT_MS = 1000L;
    private static final long MAX_TIMEOUT_MS = 600000L;
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private final byte[] tokenDigest;
    private final List<Pattern> allowedHosts;
    private final Set<Integer> blockedLocalPorts;
    private final int maxResponseBytes;
    private final int maxRequestBytes;
    private final Semaphore inFlight;

    @Autowired
    public RelayService(
            @Value("${relay.token:}") String token,
            @Value("${relay.allowed-hosts:}") String allowedHosts,
            @Value("${relay.blocked-local-ports:}") String blockedLocalPorts,
            @Value("${server.port:8093}") int serverPort,
            @Value("${relay.max-response-bytes:10485760}") int maxResponseBytes,
            @Value("${relay.max-request-bytes:20971520}") int maxRequestBytes,
            @Value("${relay.max-in-flight:50}") int maxInFlight) {
        this.tokenDigest = token == null || token.trim().isEmpty() ? null : sha256(token.trim());
        this.allowedHosts = parseAllowedHosts(allowedHosts);
        this.blockedLocalPorts = new HashSet<Integer>();
        this.blockedLocalPorts.add(serverPort);
        for (String p : String.valueOf(blockedLocalPorts == null ? "" : blockedLocalPorts).split(",")) {
            if (p.trim().isEmpty()) {
                continue;
            }
            try {
                this.blockedLocalPorts.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException e) {
                LOG.warn("Ignoring invalid relay.blocked-local-ports entry '{}'", p.trim());
            }
        }
        this.maxResponseBytes = maxResponseBytes;
        this.maxRequestBytes = maxRequestBytes;
        this.inFlight = new Semaphore(maxInFlight);
        if (this.tokenDigest == null) {
            LOG.info("Relay disabled (relay.token is not set)");
        } else {
            LOG.info("Relay enabled (allowed hosts: {}, blocked local ports: {})",
                    this.allowedHosts.isEmpty() ? "any" : this.allowedHosts.size() + " pattern(s)", this.blockedLocalPorts);
        }
    }

    public boolean isEnabled() {
        return tokenDigest != null;
    }

    /** Constant-time check of an "Authorization: Bearer ..." header value. */
    public boolean isAuthorized(String authorization) {
        if (tokenDigest == null) {
            return false;
        }
        String supplied = "";
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            supplied = authorization.substring(7).trim();
        }
        return MessageDigest.isEqual(sha256(supplied), tokenDigest);
    }

    /**
     * @param in the JSON relay request
     * @param declaredLength Content-Length of the incoming request, or -1
     */
    public ObjectNode relay(InputStream in, long declaredLength) throws RelayException {
        if (!inFlight.tryAcquire()) {
            throw new RelayException(429, "too_many_requests", "Too many relayed calls in progress");
        }
        try {
            if (declaredLength > maxRequestBytes) {
                throw new RelayException(413, "request_too_large", "Relay request too large");
            }
            return relay(parse(readLimited(in)));
        } finally {
            inFlight.release();
        }
    }

    private byte[] readLimited(InputStream in) throws RelayException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int size = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                size += n;
                if (size > maxRequestBytes) {
                    throw new RelayException(413, "request_too_large", "Relay request too large");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RelayException(400, "invalid_request", "Could not read the relay request");
        }
    }

    private JsonNode parse(byte[] bytes) throws RelayException {
        try {
            JsonNode node = bytes.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new RelayException(400, "invalid_request", "Missing relay request");
            }
            return node;
        } catch (IOException e) {
            throw new RelayException(400, "invalid_request", "Body is not valid JSON");
        }
    }

    private ObjectNode relay(JsonNode input) throws RelayException {
        String method = text(input.get("method"), "GET").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new RelayException(400, "invalid_request", "Unsupported HTTP method: " + method);
        }
        URI uri = RelayHttpExecutor.parseUri(text(input.get("url"), ""));
        if (uri == null) {
            throw new RelayException(400, "invalid_request", "Invalid url");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new RelayException(400, "invalid_request", "Only http and https URLs are supported");
        }
        if (uri.getHost() == null) {
            throw new RelayException(400, "invalid_request", "Invalid url");
        }
        JsonNode headersNode = input.get("headers");
        if (headersNode != null && !headersNode.isNull() && !headersNode.isObject()) {
            throw new RelayException(400, "invalid_request", "headers must be an object");
        }
        List<String[]> headers = new ArrayList<String[]>();
        if (headersNode != null && headersNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = headersNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!HEADER_NAME.matcher(field.getKey()).matches()) {
                    throw new RelayException(400, "invalid_request", "Invalid header name");
                }
                JsonNode value = field.getValue();
                if (value.isArray()) {
                    for (JsonNode v : value) {
                        addHeader(headers, field.getKey(), v);
                    }
                } else {
                    addHeader(headers, field.getKey(), value);
                }
            }
        }
        byte[] body = null;
        String bodyBase64 = text(input.get("bodyBase64"), "");
        if (!bodyBase64.isEmpty()) {
            try {
                body = Base64.getMimeDecoder().decode(bodyBase64);
            } catch (IllegalArgumentException e) {
                throw new RelayException(400, "invalid_request", "bodyBase64 is not valid base64");
            }
        }

        RelayHttpExecutor.Request request = new RelayHttpExecutor.Request();
        request.method = method;
        request.uri = uri;
        request.headers = headers;
        request.body = body;
        request.followRedirects = !(input.get("followRedirects") != null && input.get("followRedirects").isBoolean()
                && !input.get("followRedirects").asBoolean());

        RelayHttpExecutor.Options options = new RelayHttpExecutor.Options();
        options.timeoutMs = timeout(input.get("timeoutMs"));
        options.acceptUnsignedSsl = input.get("acceptUnsignedSsl") != null && input.get("acceptUnsignedSsl").isBoolean()
                && input.get("acceptUnsignedSsl").asBoolean();
        options.maxBodyBytes = maxResponseBytes;
        options.blockedLocalPorts = blockedLocalPorts;
        options.urlCheck = new RelayHttpExecutor.UrlCheck() {
            @Override
            public String refuse(URI target) {
                if (allowedHosts.isEmpty()) {
                    return null;
                }
                String host = target.getHost().toLowerCase(Locale.ROOT);
                for (Pattern p : allowedHosts) {
                    if (p.matcher(host).matches()) {
                        return null;
                    }
                }
                return "Host " + host + " is not in relay.allowed-hosts";
            }
        };

        // Never log the query string nor any header: they routinely carry credentials.
        String where = RelayHttpExecutor.origin(uri, false) + (uri.getRawPath() == null ? "" : uri.getRawPath());
        long started = System.currentTimeMillis();
        try {
            RelayHttpExecutor.Result result = RelayHttpExecutor.execute(request, options);
            long durationMs = System.currentTimeMillis() - started;
            LOG.info("{} {} -> {} ({}ms)", method, where, result.status, durationMs);
            ObjectNode out = MAPPER.createObjectNode();
            out.put("status", result.status);
            out.put("statusText", result.statusText);
            com.fasterxml.jackson.databind.node.ArrayNode headersOut = out.putArray("headers");
            for (String[] h : result.headers) {
                headersOut.addArray().add(h[0]).add(h[1]);
            }
            out.put("bodyBase64", Base64.getEncoder().encodeToString(result.body));
            out.put("truncated", result.truncated);
            out.put("durationMs", durationMs);
            out.put("finalUrl", result.finalUrl);
            return out;
        } catch (RelayException e) {
            LOG.info("{} {} -> failed: {} ({}ms)", method, where, e.getCode(), System.currentTimeMillis() - started);
            throw e;
        }
    }

    private static void addHeader(List<String[]> headers, String name, JsonNode value) throws RelayException {
        if (value == null || value.isNull()) {
            return;
        }
        String v = value.asText();
        if (v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\0') >= 0) {
            throw new RelayException(400, "invalid_request", "Invalid header value for " + name);
        }
        headers.add(new String[]{name, v});
    }

    private static String text(JsonNode node, String fallback) {
        return node == null || node.isNull() ? fallback : node.asText(fallback);
    }

    private static long timeout(JsonNode node) {
        double value = 0;
        if (node != null && node.isNumber()) {
            value = node.asDouble();
        } else if (node != null && node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException e) {
                value = 0;
            }
        }
        if (Double.isNaN(value) || value == 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return (long) Math.min(MAX_TIMEOUT_MS, Math.max(MIN_TIMEOUT_MS, value));
    }

    /** "*.corp.example.com, api.example.com" -> patterns; empty list means any host. */
    static List<Pattern> parseAllowedHosts(String text) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String p : String.valueOf(text == null ? "" : text).split(",")) {
            p = p.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) {
                continue;
            }
            StringBuilder regex = new StringBuilder();
            for (char c : p.toCharArray()) {
                if (c == '*') {
                    regex.append(".*");
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            patterns.add(Pattern.compile(regex.toString()));
        }
        return patterns;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
