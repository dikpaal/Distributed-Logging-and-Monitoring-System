package com.logging.processing.kafka;

import com.logging.common.dto.LogEvent;
import com.logging.processing.cache.LogCacheService;
import com.logging.processing.entity.LogEntity;
import com.logging.processing.repository.LogRepository;
import com.logging.processing.service.IdempotencyService;
import org.apache.kafka.common.header.Header;
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
public class LogConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogConsumer.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final LogRepository logRepository;
    private final LogCacheService logCacheService;
    private final IdempotencyService idempotencyService;

    public LogConsumer(
            LogRepository logRepository,
            LogCacheService logCacheService,
            IdempotencyService idempotencyService) {
        this.logRepository = logRepository;
        this.logCacheService = logCacheService;
        this.idempotencyService = idempotencyService;
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

        // Extract idempotency key from headers
        String idempotencyKey = extractIdempotencyKey(headers);

        log.info("Received log: partition={}, offset={}, service={}, severity={}, idempotencyKey={}",
                partition, offset, logEvent.serviceName(), logEvent.severity(), idempotencyKey);

        // Check idempotency - skip if already processed
        if (!idempotencyService.tryMarkAsProcessed(idempotencyKey)) {
            log.info("Skipping duplicate message: partition={}, offset={}, idempotencyKey={}",
                    partition, offset, idempotencyKey);
            acknowledgment.acknowledge();
            return;
        }

        try {
            LogEntity entity = new LogEntity(
                    logEvent.serviceName(),
                    logEvent.severity().name(),
                    logEvent.message(),
                    logEvent.timestamp(),
                    logEvent.traceId(),
                    logEvent.host(),
                    logEvent.metadata()
            );

            // Persist to PostgreSQL
            logRepository.save(entity);
            log.debug("Persisted log to database: id={}", entity.getId());

            // Cache in Redis
            logCacheService.cacheRecentLog(entity);

            acknowledgment.acknowledge();
            log.debug("Acknowledged offset: {}", offset);

        } catch (Exception e) {
            log.error("Failed to process log: partition={}, offset={}, error={}",
                    partition, offset, e.getMessage(), e);
            // Remove idempotency marker so it can be retried
            idempotencyService.removeProcessedKey(idempotencyKey);
            // Re-throw to trigger retry/DLQ handling
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
