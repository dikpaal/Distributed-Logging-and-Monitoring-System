package com.logging.alert.service;

import com.logging.alert.entity.AlertEntity;
import com.logging.alert.model.AlertRule;
import com.logging.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRuleService alertRuleService;
    private final SlidingWindowService slidingWindowService;
    private final AlertRepository alertRepository;

    public AlertEvaluationService(AlertRuleService alertRuleService,
                                   SlidingWindowService slidingWindowService,
                                   AlertRepository alertRepository) {
        this.alertRuleService = alertRuleService;
        this.slidingWindowService = slidingWindowService;
        this.alertRepository = alertRepository;
    }

    @Scheduled(fixedRateString = "${app.alert.evaluation-interval-ms:5000}")
    public void evaluateRules() {
        log.debug("Evaluating alert rules...");

        for (AlertRule rule : alertRuleService.getAllRules()) {
            evaluateRule(rule);
        }
    }

    private void evaluateRule(AlertRule rule) {
        String windowKey = rule.getWindowKey(rule.getServiceName());
        long count = slidingWindowService.getCount(windowKey, rule.getWindowSeconds());

        log.debug("Rule {}: count={}, threshold={}", rule.getName(), count, rule.getThreshold());

        if (count >= rule.getThreshold()) {
            // Check if we already triggered an alert recently (cooldown)
            if (!isInCooldown(rule)) {
                triggerAlert(rule, count);
            } else {
                log.debug("Rule {} is in cooldown, skipping alert", rule.getName());
            }
        }

        // Cleanup old entries
        slidingWindowService.cleanupOldEntries(windowKey, rule.getWindowSeconds());
    }

    private boolean isInCooldown(AlertRule rule) {
        int cooldownSeconds = alertRuleService.getCooldownSeconds();
        Instant since = Instant.now().minusSeconds(cooldownSeconds);
        Optional<AlertEntity> recentAlert = alertRepository.findRecentAlertByRuleName(rule.getName(), since);
        return recentAlert.isPresent();
    }

    private void triggerAlert(AlertRule rule, long count) {
        log.warn("ALERT TRIGGERED: {} - count={} exceeds threshold={}",
                rule.getName(), count, rule.getThreshold());

        AlertEntity alert = new AlertEntity();
        alert.setRuleName(rule.getName());
        alert.setServiceName(rule.getServiceName());
        alert.setSeverity(rule.getSeverity());
        alert.setCount(count);
        alert.setThreshold((long) rule.getThreshold());
        alert.setWindowSeconds(rule.getWindowSeconds());
        alert.setTriggeredAt(Instant.now());
        alert.setMessage(String.format("Alert rule '%s' triggered: %d events in %d seconds (threshold: %d)",
                rule.getName(), count, rule.getWindowSeconds(), rule.getThreshold()));

        alertRepository.save(alert);
        log.info("Alert saved: {}", alert.getMessage());
    }
}
