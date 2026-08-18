/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

    // Live traffic entries carry full request/response bodies (page HTML, JS/CSS bundles, ...),
    // which can be several hundred KB. Tomcat's default WebSocket text buffer (8KB) and Spring's
    // default STOMP message size limit (64KB) silently drop anything larger, so only the smallest
    // entries were ever reaching the front-end.
    private static final int MAX_TRAFFIC_MESSAGE_SIZE = 10 * 1024 * 1024;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        //config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
         registry.addEndpoint("/chat");
         registry.addEndpoint("/chat").withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_TRAFFIC_MESSAGE_SIZE);
        registration.setSendBufferSizeLimit(MAX_TRAFFIC_MESSAGE_SIZE);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TRAFFIC_MESSAGE_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TRAFFIC_MESSAGE_SIZE);
        return container;
    }
}
