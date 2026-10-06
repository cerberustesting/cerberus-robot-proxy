package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.cerberus.robot.proxy.application.OAuthTestSupport.get;
import static org.cerberus.robot.proxy.application.OAuthTestSupport.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** robotproxy.auth.mode=oauth: all or nothing, every route needs a valid JWT; relay.token is ignored. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "robotproxy.auth.mode=oauth",
    "relay.token=secret",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + OAuthTestSupport.ISSUER})
@Import(OAuthTestSupport.DecoderConfig.class)
class ApplicationOAuthTest {

    @Autowired
    private TestRestTemplate rest;

    private void assertUnauthorized(ResponseEntity<String> r) {
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        assertTrue(r.getBody().contains("\"code\":\"unauthorized\""), r.getBody());
    }

    @Test
    void openPathsStayPublic() {
        assertEquals(HttpStatus.OK, get(rest, "/check", null).getStatusCode());
        assertEquals(HttpStatus.OK, get(rest, "/", null).getStatusCode());
        assertEquals(HttpStatus.OK, get(rest, "/v3/api-docs", null).getStatusCode());
        assertEquals(HttpStatus.OK, get(rest, "/webjars/jquery/jquery.min.js", null).getStatusCode());
    }

    @Test
    void anyValidTokenGivesAccessToEveryRoute() throws Exception {
        String jwt = token(300);
        for (String path : new String[]{"/management", "/getProxyList", "/relay/check", "/chat/info"}) {
            assertEquals(HttpStatus.OK, get(rest, path, jwt).getStatusCode(), path);
        }
    }

    @Test
    void missingWrongOrExpiredTokenGivesNothing() throws Exception {
        for (String path : new String[]{"/management", "/relay/check", "/chat/info"}) {
            assertUnauthorized(get(rest, path, null));
            assertUnauthorized(get(rest, path, "wrong"));
            assertUnauthorized(get(rest, path, token(-120)));
        }
    }

    @Test
    void staticTokenIsNotAcceptedInOauthMode() {
        assertUnauthorized(get(rest, "/relay/check", "secret"));
        assertUnauthorized(get(rest, "/management", "secret"));
    }
}
