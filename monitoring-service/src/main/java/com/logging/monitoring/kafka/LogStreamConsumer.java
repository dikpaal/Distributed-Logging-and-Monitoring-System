package com.logging.monitoring.kafka;

import com.logging.common.dto.LogEvent;
import com.logging.monitoring.websocket.LogWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class LogStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogStreamConsumer.class);

    private final LogWebSocketHandler webSocketHandler;

    public LogStreamConsumer(LogWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = "logs.ingested", groupId = "log-stream-processors")
    public void consume(LogEvent logEvent) {
        log.debug("Streaming log to {} clients: {} - {}",
                webSocketHandler.getActiveSessionCount(),
                logEvent.serviceName(),
                logEvent.message());

        webSocketHandler.broadcast(logEvent);
    }
}
