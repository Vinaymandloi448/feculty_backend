package com.facultyconnect.feculty_backend.config;

import com.facultyconnect.feculty_backend.websocket.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * Configuration for WebSocket with STOMP message broker.
 *
 * Endpoint: /ws
 * Application destination prefix: /app (Client sends to /app/...)
 * Simple broker destination prefix: /topic (Server broadcasts to /topic/...)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthInterceptor webSocketAuthInterceptor,
            @Value("${cors.allowed-origins:https://fecultyconnectfrontend-z4lt.vercel.app,http://localhost:3000,http://localhost:5173}") String allowedOrigins) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // Native STOMP WebSocket endpoint
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);

        // SockJS fallback endpoint for browsers or clients using SockJS
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
