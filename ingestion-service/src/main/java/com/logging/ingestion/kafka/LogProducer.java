package com.logging.ingestion.kafka;

import com.logging.common.dto.LogEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
public class LogProducer {

    private static final Logger log = LoggerFactory.getLogger(LogProducer.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final String topic;

    public LogProducer(
            KafkaTemplate<String, LogEvent> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, LogEvent>> send(LogEvent logEvent, String idempotencyKey) {
        String key = logEvent.traceId() != null ? logEvent.traceId() : logEvent.serviceName();

        log.debug("Sending log to Kafka: topic={}, key={}, service={}, idempotencyKey={}",
                topic, key, logEvent.serviceName(), idempotencyKey);

        ProducerRecord<String, LogEvent> record = new ProducerRecord<>(topic, key, logEvent);

        // Add idempotency key as header if present
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            record.headers().add(new RecordHeader(
                    IDEMPOTENCY_KEY_HEADER,
                    idempotencyKey.getBytes(StandardCharsets.UTF_8)
            ));
        }

        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send log to Kafka: {}", ex.getMessage());
                    } else {
                        log.debug("Log sent to Kafka: partition={}, offset={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
