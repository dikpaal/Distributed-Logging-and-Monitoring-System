package com.logging.alert.service;

import com.logging.alert.config.AlertProperties;
import com.logging.alert.model.AlertRule;
import com.logging.common.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);
    private final AlertProperties alertProperties;

    public AlertRuleService(AlertProperties alertProperties) {
        this.alertProperties = alertProperties;
        log.info("Loaded {} alert rules", alertProperties.getRules().size());
        for (AlertRule rule : alertProperties.getRules()) {
            log.info("Rule: {} - service={}, severity={}, threshold={}, window={}s",
                    rule.getName(), rule.getServiceName(), rule.getSeverity(),
                    rule.getThreshold(), rule.getWindowSeconds());
        }
    }

    public List<AlertRule> getAllRules() {
        return alertProperties.getRules();
    }

    public List<AlertRule> getMatchingRules(LogEvent logEvent) {
        String severity = logEvent.severity() != null ? logEvent.severity().name() : null;
        return alertProperties.getRules().stream()
                .filter(rule -> rule.matches(logEvent.serviceName(), severity))
                .toList();
    }

    public int getCooldownSeconds() {
        return alertProperties.getCooldownSeconds();
    }
}
