package org.cerberus.robot.proxy.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A wrong authentication setup must stop the startup instead of silently leaving the services open. */
class ApplicationInvalidAuthConfigTest {

    private static Throwable failure(String... properties) {
        String[] all = new String[properties.length + 3];
        System.arraycopy(properties, 0, all, 0, properties.length);
        all[properties.length] = "server.port=0";
        all[properties.length + 1] = "relay.token=";
        all[properties.length + 2] = "robotproxy.auth.token=";
        // Command-line arguments: SpringApplicationBuilder.properties() has the lowest priority and
        // application.properties (robotproxy.auth.mode=none) would override it.
        for (int i = 0; i < all.length; i++) {
            all[i] = "--" + all[i];
        }
        Exception e = assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Application.class)
                    .web(WebApplicationType.SERVLET).run(all)) {
                // must not start
            }
        });
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    @Test
    void unknownMode() {
        assertTrue(failure("robotproxy.auth.mode=basic").getMessage().contains("Unknown robotproxy.auth.mode"));
    }

    @Test
    void tokenModeWithoutToken() {
        assertTrue(failure("robotproxy.auth.mode=token").getMessage().contains("robotproxy.auth.token"));
    }

    @Test
    void oauthModeWithoutIssuer() {
        assertTrue(failure("robotproxy.auth.mode=oauth").getMessage().contains("issuer-uri"));
    }
}
