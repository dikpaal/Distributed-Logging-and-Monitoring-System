package com.logging.ingestion.kafka;

import com.logging.common.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class LogProducer {

    private static final Logger log = LoggerFactory.getLogger(LogProducer.class);

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final String topic;

    public LogProducer(
            KafkaTemplate<String, LogEvent> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, LogEvent>> send(LogEvent logEvent) {
        String key = logEvent.traceId() != null ? logEvent.traceId() : logEvent.serviceName();

        log.debug("Sending log to Kafka: topic={}, key={}, service={}",
                topic, key, logEvent.serviceName());

        return kafkaTemplate.send(topic, key, logEvent)
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
