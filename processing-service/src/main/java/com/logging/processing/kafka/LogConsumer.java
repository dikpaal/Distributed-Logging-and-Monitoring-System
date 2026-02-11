package com.logging.processing.kafka;

import com.logging.common.dto.LogEvent;
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

        if (logEvent.traceId() != null) {
            log.debug("TraceId: {}", logEvent.traceId());
        }

        // TODO: Phase 6 - persist to PostgreSQL
        // TODO: Phase 7 - cache in Redis

        acknowledgment.acknowledge();
        log.debug("Acknowledged offset: {}", offset);
    }
}
