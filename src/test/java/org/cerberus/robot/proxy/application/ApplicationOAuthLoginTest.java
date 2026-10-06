package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.cerberus.robot.proxy.application.OAuthTestSupport.token;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** oauth mode with the browser login of the UI (ui.client-id set): login redirect + session, Bearer unchanged. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "robotproxy.auth.mode=oauth",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + OAuthTestSupport.ISSUER,
    "robotproxy.auth.oauth2.ui.client-id=cerberus-robot-proxy",
    "robotproxy.auth.oauth2.ui.client-secret=s3cr3t"})
@AutoConfigureMockMvc
@Import(OAuthTestSupport.DecoderConfig.class)
class ApplicationOAuthLoginTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void browserNavigationIsRedirectedToTheKeycloakLogin() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/keycloak"));
        mvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound());
    }

    @Test
    void apiCallsWithoutSessionGetAJson401NotARedirect() throws Exception {
        mvc.perform(get("/management").accept(MediaType.ALL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mvc.perform(get("/getProxyList").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        // a page navigation carrying a Bearer token is an API client, not a browser
        mvc.perform(get("/management").accept(MediaType.TEXT_HTML).header(HttpHeaders.AUTHORIZATION, "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assetsAndHealthCheckStayPublic() throws Exception {
        mvc.perform(get("/check")).andExpect(status().isOk());
        mvc.perform(get("/css/loader.css")).andExpect(status().isOk());
    }

    @Test
    void bearerClientsKeepWorking() throws Exception {
        String jwt = "Bearer " + token(300);
        for (String path : new String[]{"/management", "/relay/check", "/chat/info"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, jwt)).andExpect(status().isOk());
        }
    }

    @Test
    void anyLoggedInUserGetsTheUiAndTheApi() throws Exception {
        mvc.perform(get("/").with(oidcLogin()).accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
        mvc.perform(get("/management").with(oidcLogin())).andExpect(status().isOk());
        mvc.perform(get("/chat/info").with(oidcLogin())).andExpect(status().isOk());
        mvc.perform(get("/relay/check").with(oidcLogin())).andExpect(status().isOk());
    }
}
