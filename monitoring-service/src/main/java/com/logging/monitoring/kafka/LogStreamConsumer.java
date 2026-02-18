package com.logging.monitoring.kafka;

import com.logging.common.dto.LogEvent;
import com.logging.monitoring.cache.LogCacheService;
import com.logging.monitoring.entity.LogEntity;
import com.logging.monitoring.repository.LogRepository;
import com.logging.monitoring.service.IdempotencyService;
import com.logging.monitoring.websocket.LogWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class LogStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogStreamConsumer.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final LogRepository logRepository;
    private final LogCacheService logCacheService;
    private final IdempotencyService idempotencyService;
    private final LogWebSocketHandler webSocketHandler;

    public LogStreamConsumer(
            LogRepository logRepository,
            LogCacheService logCacheService,
            IdempotencyService idempotencyService,
            LogWebSocketHandler webSocketHandler) {
        this.logRepository = logRepository;
        this.logCacheService = logCacheService;
        this.idempotencyService = idempotencyService;
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            @Payload LogEvent logEvent,
            @Headers Map<String, Object> headers,
            Acknowledgment acknowledgment) {

        int partition = (int) headers.get(KafkaHeaders.RECEIVED_PARTITION);
        long offset = (long) headers.get(KafkaHeaders.OFFSET);
        String idempotencyKey = extractIdempotencyKey(headers);

        log.debug("Received log: partition={}, offset={}, service={}, severity={}",
                partition, offset, logEvent.serviceName(), logEvent.severity());

        // Check idempotency - skip if already processed
        if (!idempotencyService.tryMarkAsProcessed(idempotencyKey)) {
            log.info("Skipping duplicate message: partition={}, offset={}", partition, offset);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Persist to PostgreSQL
            LogEntity entity = new LogEntity(
                    logEvent.serviceName(),
                    logEvent.severity().name(),
                    logEvent.message(),
                    logEvent.timestamp(),
                    logEvent.traceId(),
                    logEvent.host(),
                    logEvent.metadata()
            );
            logRepository.save(entity);

            // Cache in Redis
            logCacheService.cacheRecentLog(entity);

            // Broadcast to WebSocket clients
            webSocketHandler.broadcast(logEvent);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process log: partition={}, offset={}, error={}",
                    partition, offset, e.getMessage(), e);
            idempotencyService.removeProcessedKey(idempotencyKey);
            throw e;
        }
    }

    private String extractIdempotencyKey(Map<String, Object> headers) {
        Object headerValue = headers.get(IDEMPOTENCY_KEY_HEADER);
        if (headerValue == null) {
            return null;
        }
        if (headerValue instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        } else if (headerValue instanceof String str) {
            return str;
        }
        return null;
    }
}
