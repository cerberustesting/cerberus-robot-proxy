package org.cerberus.application;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@ComponentScan (basePackages = {"org.cerberus.application",
                                "org.cerberus.sikuli",
                                "org.cerberus.proxy"})
public class Application {

    public static void main(String[] args) {
        /**
         * Parse Arguments //
         */
        
        SpringApplication.run(Application.class, args);

    }

   
}
