package com.logging.processing.kafka;

import com.logging.common.dto.LogEvent;
import com.logging.processing.entity.LogEntity;
import com.logging.processing.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class LogConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogConsumer.class);

    private final LogRepository logRepository;

    public LogConsumer(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            @Payload LogEvent logEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received log: partition={}, offset={}, service={}, severity={}, message={}",
                partition, offset, logEvent.serviceName(), logEvent.severity(), logEvent.message());

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

            logRepository.save(entity);
            log.debug("Persisted log to database: id={}", entity.getId());

            // TODO: Phase 7 - cache in Redis

            acknowledgment.acknowledge();
            log.debug("Acknowledged offset: {}", offset);

        } catch (Exception e) {
            log.error("Failed to process log: {}", e.getMessage(), e);
            // Don't acknowledge - will be retried
        }
    }
}
