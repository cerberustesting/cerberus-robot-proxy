package org.cerberus.robot.proxy.application;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence defaults required by the Spring Boot 3 migration. Done here rather than in
 * application.properties so they survive a custom --spring.config.location and any user override still wins.
 */
public class CompatDefaultsPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        // Spring Boot >= 2.3+ no longer includes the exception message in the error body: keep the 2.2 behaviour.
        defaults.put("server.error.include-message", "always");
        environment.getPropertySources().addLast(new MapPropertySource("robotProxyCompatDefaults", defaults));
    }
}
