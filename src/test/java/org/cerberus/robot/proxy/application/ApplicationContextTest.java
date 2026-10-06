package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the real Spring context (relay enabled) and calls the routes over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "relay.token=secret")
class ApplicationContextTest {

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> get(String path, String bearer) {
        HttpHeaders h = new HttpHeaders();
        if (bearer != null) {
            h.set("Authorization", "Bearer " + bearer);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void check() {
        ResponseEntity<String> r = get("/check", null);
        assertEquals(HttpStatus.OK, r.getStatusCode());
    }

    @Test
    void relayCheckRequiresToken() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/relay/check", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/relay/check", "wrong").getStatusCode());
        ResponseEntity<String> ok = get("/relay/check", "secret");
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertTrue(ok.getBody().contains("\"ok\":true"));
    }

    @Test
    void swaggerSpecAndWebjarsAndSockJs() {
        ResponseEntity<String> spec = get("/v3/api-docs", null);
        assertEquals(HttpStatus.OK, spec.getStatusCode());
        assertTrue(spec.getBody().contains("/check"));
        assertEquals(HttpStatus.OK, get("/swagger-ui/index.html", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/webjars/jquery/jquery.min.js", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/webjars/jquery/3.4.1/jquery.min.js", null).getStatusCode());
        assertEquals(HttpStatus.OK, get("/chat/info", null).getStatusCode());
    }
}
