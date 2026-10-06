package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.cerberus.robot.proxy.application.OAuthTestSupport.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Token mode without robotproxy.auth.token: relay.token is used, so existing setups just switch the mode. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "robotproxy.auth.mode=token", "relay.token=legacy"})
class ApplicationTokenModeFallbackTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void relayTokenIsTheDefaultToken() {
        assertEquals(HttpStatus.UNAUTHORIZED, get(rest, "/management", null).getStatusCode());
        assertEquals(HttpStatus.OK, get(rest, "/management", "legacy").getStatusCode());
    }
}
