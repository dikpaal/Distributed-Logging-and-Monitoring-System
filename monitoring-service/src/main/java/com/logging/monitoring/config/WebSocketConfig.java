package com.logging.monitoring.config;

import com.logging.monitoring.websocket.LogWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogWebSocketHandler logWebSocketHandler;

    public WebSocketConfig(LogWebSocketHandler logWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }
}
