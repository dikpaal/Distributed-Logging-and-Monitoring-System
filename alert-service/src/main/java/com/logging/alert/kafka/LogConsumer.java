package com.logging.alert.kafka;

import com.logging.alert.model.AlertRule;
import com.logging.alert.service.AlertRuleService;
import com.logging.alert.service.SlidingWindowService;
import com.logging.common.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class LogConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogConsumer.class);

    private final AlertRuleService alertRuleService;
    private final SlidingWindowService slidingWindowService;

    public LogConsumer(AlertRuleService alertRuleService, SlidingWindowService slidingWindowService) {
        this.alertRuleService = alertRuleService;
        this.slidingWindowService = slidingWindowService;
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

        try {
            log.debug("Received log from partition {} offset {}: service={}, severity={}",
                    partition, offset, logEvent.serviceName(),
                    logEvent.severity() != null ? logEvent.severity().name() : "null");

            // Get matching rules and update their sliding windows
            List<AlertRule> matchingRules = alertRuleService.getMatchingRules(logEvent);

            Instant timestamp = logEvent.timestamp() != null ? logEvent.timestamp() : Instant.now();

            for (AlertRule rule : matchingRules) {
                String windowKey = rule.getWindowKey(logEvent.serviceName());
                slidingWindowService.addEntry(windowKey, timestamp);
                log.debug("Updated window {} for rule {}", windowKey, rule.getName());
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing log event from partition {} offset {}: {}",
                    partition, offset, e.getMessage(), e);
            throw e;  // Re-throw to trigger error handler
        }
    }
}
