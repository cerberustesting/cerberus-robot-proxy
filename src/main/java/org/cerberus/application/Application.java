package org.cerberus.application;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@ComponentScan (basePackages = {"org.cerberus.application",
                                "org.cerberus.sikuli",
                                "org.cerberus.screenrecorder",
                                "org.cerberus.proxy"})
@EnableScheduling
@PropertySource("classpath:application.properties")
public class Application {

    public static void main(String[] args) {
        /**
         * Parse Arguments //
         */
        
        SpringApplication.run(Application.class, args);

    }

   
}
