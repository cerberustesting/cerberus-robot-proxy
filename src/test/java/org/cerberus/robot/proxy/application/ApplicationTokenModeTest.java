package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** robotproxy.auth.mode=token: one shared token, as Bearer header or typed once on the /login page. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "robotproxy.auth.mode=token", "robotproxy.auth.token=tok", "relay.token="})
@AutoConfigureMockMvc
class ApplicationTokenModeTest {

    private static final String[] PROTECTED = {"/management", "/getProxyList", "/chat/info", "/relay/check"};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRestTemplate rest;

    private MockHttpSession login() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").param("token", "tok"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
        assertNotNull(session, "the login must create a session");
        return session;
    }

    @Test
    void openPathsStayPublic() throws Exception {
        mvc.perform(get("/check")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"token\"")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/css/loader.css")).andExpect(status().isOk());
    }

    @Test
    void bearerTokenGivesAccessToEveryRoute() throws Exception {
        for (String path : PROTECTED) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthorized"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer wrong")).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer tok")).andExpect(status().isOk());
        }
    }

    @Test
    void browserNavigationIsRedirectedToTheLoginPage() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));
        // API calls (fetch/XHR) get a JSON 401, not a redirect
        mvc.perform(get("/management").accept(MediaType.ALL)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongTokenOnTheLoginPageIsRefused() throws Exception {
        mvc.perform(post("/login").param("token", "wrong")).andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Invalid token")));
        mvc.perform(post("/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/management").session(new MockHttpSession())).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPageGivesASessionThatWorksOnEveryRoute() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/").session(session).accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
        for (String path : PROTECTED) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk());
        }
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/logout").session(session)).andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));
        mvc.perform(get("/management").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void controllerFailuresAreNotMaskedAsUnauthorized() {
        // Real HTTP call: MockMvc does not do the error dispatch to /error that this checks.
        assertEquals(HttpStatus.UNAUTHORIZED, OAuthTestSupport.get(rest, "/getStats", null).getStatusCode());
        // unknown proxy: the controller throws, the real 500 must reach the authenticated client
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, OAuthTestSupport.get(rest, "/getStats", "tok").getStatusCode());
    }
}
