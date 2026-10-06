package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real context without relay.token: the relay answers 503 while /check keeps working. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "relay.token=")
class ApplicationRelayDisabledTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void relayDisabled() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rest.getForEntity("/relay/check", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.getForEntity("/check", String.class).getStatusCode());
    }
}
