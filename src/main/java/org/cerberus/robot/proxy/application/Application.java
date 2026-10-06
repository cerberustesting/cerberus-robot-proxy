package org.cerberus.robot.proxy.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

// No generated "user" password: authentication is either off or JWT-based (see SecurityConfig).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(basePackages = {"org.cerberus.robot.proxy.application",
    "org.cerberus.robot.proxy.repository",
    "org.cerberus.robot.proxy.proxy",
    "org.cerberus.robot.proxy.relay"})
@EnableScheduling
@PropertySource("classpath:application.properties")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
