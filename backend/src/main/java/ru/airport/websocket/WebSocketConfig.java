package ru.airport.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import ru.airport.config.AirportProperties;

/**
 * STOMP поверх WebSocket: эндпоинт {@code /ws}, брокер с префиксом {@code /topic} (AGENTS §8).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AirportProperties airportProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = airportProperties.getAllowedOrigins().toArray(new String[0]);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
        // Raw WebSocket для мобильных клиентов (Flutter).
        // Нативные приложения Origin не шлют вовсе, поэтому паттерн их не касается,
        // а Flutter-web (используется для QA) шлёт Origin и без него хендшейк отклоняется.
        registry.addEndpoint("/ws/raw")
                .setAllowedOriginPatterns("*");
    }
}
