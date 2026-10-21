package org.cerberus.robot.proxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.cerberus.robot.proxy.application.Application;
import org.cerberus.robot.proxy.repository.MySessionProxiesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * /getHar and /getHarMD5 as the callers know them, now that the HAR is streamed from the proxy instead of
 * being held in memory. A fake mitmdump API plays the proxy.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GetHarContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MySessionProxiesRepository repository;

    private HttpServer fakeMitm;
    private Process fakeProcess;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private volatile byte[] har;
    private volatile int status = 200;
    private String uuid;

    @BeforeEach
    void startFakeMitm() throws Exception {
        fakeMitm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeMitm.createContext("/har", exchange -> {
            lastQuery.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawQuery());
            if (status != 200) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            // As mitmdump's: no Content-Length, the end of the body is the end of the connection.
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(har);
            exchange.close();
        });
        fakeMitm.start();

        fakeProcess = new ProcessBuilder("sleep", "60").start();   // a session that has a mitmdump
        MySessionProxies msp = new MySessionProxies();
        uuid = UUID.randomUUID().toString();
        msp.setUuid(UUID.fromString(uuid));
        msp.setMitmProcess(fakeProcess);
        msp.setMitmApiPort(fakeMitm.getAddress().getPort());
        repository.addMySessionProxies(uuid, msp);
    }

    @AfterEach
    void stopFakeMitm() {
        repository.removeMySessionProxies(uuid);
        fakeMitm.stop(0);
        fakeProcess.destroyForcibly();
    }

    private static byte[] bigHar(int entries) {
        StringBuilder sb = new StringBuilder("{\"log\": {\"entries\": [");
        for (int i = 0; i < entries; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"text\": \"é \\\" \\n ").append("x".repeat(1000)).append(i).append("\"}");
        }
        return sb.append("]}}").toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The request exactly as given: no header the client would add by itself. */
    private HttpResponse<byte[]> get(String path, String accept) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
            if (accept != null) {
                request.header("Accept", accept);
            }
            return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String md5(byte[] bytes) throws Exception {
        String hex = new BigInteger(1, MessageDigest.getInstance("MD5").digest(bytes)).toString(16);
        return "0".repeat(32 - hex.length()) + hex;
    }

    @Test
    void theHarIsRelayedAsItIs() {
        har = bigHar(20000);   // about 20 MB
        assertTrue(har.length > 20_000_000);

        HttpResponse<byte[]> r = get("/getHar?uuid=" + uuid, null);

        assertEquals(200, r.statusCode());
        assertArrayEquals(har, r.body(), "byte for byte what the proxy sent");
        assertEquals("POST mode=full", lastQuery.get());
    }

    @Test
    void theParametersReachTheProxy() {
        har = bigHar(3);
        get("/getHar?uuid=" + uuid + "&emptyResponseContentText=true&requestUrl=a%20b%26c", null);
        assertEquals("POST mode=noresponse&contains=a+b%26c", lastQuery.get());
    }

    @Test
    void theContentTypeIsTheOneOfBefore() {
        har = bigHar(3);
        String[][] expected = {
            {null, "text/plain;charset=UTF-8"},
            {"*/*", "text/plain;charset=UTF-8"},
            {"application/json", "application/json"},
            {"text/html, */*;q=0.8", "text/html;charset=UTF-8"},
            {"text/plain", "text/plain;charset=UTF-8"},
            {"application/xml", "application/xml;charset=UTF-8"},
        };
        for (String[] e : expected) {
            assertEquals(e[1], MyProxyController.harContentType(e[0]), "Accept: " + e[0]);
            assertEquals(e[1], get("/getHar?uuid=" + uuid, e[0]).headers().firstValue("Content-Type").orElse(""), "on the wire, Accept: " + e[0]);
        }
    }

    @Test
    void anUnavailableHarIsStillEmpty() {
        status = 500;
        assertEquals("{}", new String(get("/getHar?uuid=" + uuid, null).body(), StandardCharsets.UTF_8));
        assertEquals("{}", new String(get("/getHar?uuid=" + UUID.randomUUID(), null).body(), StandardCharsets.UTF_8));
        assertEquals(200, get("/getHar?uuid=" + UUID.randomUUID(), null).statusCode());
    }

    @Test
    void theMd5IsOfTheHarWithoutResponseContents() throws Exception {
        har = bigHar(5000);
        HttpResponse<byte[]> r = get("/getHarMD5?uuid=" + uuid, null);
        assertEquals(md5(har), new String(r.body(), StandardCharsets.UTF_8));
        assertEquals("POST mode=noresponse", lastQuery.get());

        status = 500;
        assertEquals(md5("{}".getBytes(StandardCharsets.UTF_8)),
                new String(get("/getHarMD5?uuid=" + uuid, null).body(), StandardCharsets.UTF_8), "as the digest of {}");
        assertEquals("99914b932bd37a50b983c5e7c90ae93b", md5("{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void anUnknownProxyForTheMd5IsStillAnError() {
        assertEquals(500, get("/getHarMD5?uuid=" + UUID.randomUUID(), null).statusCode());
    }
}
