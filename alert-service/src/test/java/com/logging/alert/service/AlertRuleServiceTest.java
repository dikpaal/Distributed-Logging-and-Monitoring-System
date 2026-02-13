package com.logging.alert.service;

import com.logging.alert.config.AlertProperties;
import com.logging.alert.model.AlertRule;
import com.logging.common.dto.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleServiceTest {

    private AlertRuleService alertRuleService;
    private AlertProperties alertProperties;

    @BeforeEach
    void setUp() {
        alertProperties = new AlertProperties();

        // Set up test rules
        AlertRule errorRule = new AlertRule("high-error-rate", null, "ERROR", 10, 60);
        AlertRule serviceDownRule = new AlertRule("service-down", "order-service", "ERROR", 5, 30);
        AlertRule allSeverityRule = new AlertRule("all-logs", "user-service", null, 100, 60);

        alertProperties.setRules(List.of(errorRule, serviceDownRule, allSeverityRule));
        alertProperties.setCooldownSeconds(60);

        alertRuleService = new AlertRuleService(alertProperties);
    }

    @Test
    void getAllRules_returnsAllConfiguredRules() {
        List<AlertRule> rules = alertRuleService.getAllRules();

        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).getName()).isEqualTo("high-error-rate");
        assertThat(rules.get(1).getName()).isEqualTo("service-down");
        assertThat(rules.get(2).getName()).isEqualTo("all-logs");
    }

    @Test
    void getMatchingRules_matchesErrorSeverity() {
        LogEvent errorLog = new LogEvent(
                "test-service",
                LogEvent.Severity.ERROR,
                "Test error message",
                Instant.now(),
                null, null, null
        );

        List<AlertRule> matchingRules = alertRuleService.getMatchingRules(errorLog);

        // Should match "high-error-rate" (ERROR severity, any service)
        assertThat(matchingRules).hasSize(1);
        assertThat(matchingRules.get(0).getName()).isEqualTo("high-error-rate");
    }

    @Test
    void getMatchingRules_matchesServiceAndSeverity() {
        LogEvent orderServiceError = new LogEvent(
                "order-service",
                LogEvent.Severity.ERROR,
                "Order failed",
                Instant.now(),
                null, null, null
        );

        List<AlertRule> matchingRules = alertRuleService.getMatchingRules(orderServiceError);

        // Should match both "high-error-rate" and "service-down"
        assertThat(matchingRules).hasSize(2);
        assertThat(matchingRules.stream().map(AlertRule::getName))
                .containsExactlyInAnyOrder("high-error-rate", "service-down");
    }

    @Test
    void getMatchingRules_matchesAllSeveritiesForService() {
        LogEvent userServiceInfo = new LogEvent(
                "user-service",
                LogEvent.Severity.INFO,
                "User logged in",
                Instant.now(),
                null, null, null
        );

        List<AlertRule> matchingRules = alertRuleService.getMatchingRules(userServiceInfo);

        // Should match "all-logs" (user-service, any severity)
        assertThat(matchingRules).hasSize(1);
        assertThat(matchingRules.get(0).getName()).isEqualTo("all-logs");
    }

    @Test
    void getMatchingRules_noMatchForInfoSeverityOnErrorRule() {
        LogEvent infoLog = new LogEvent(
                "random-service",
                LogEvent.Severity.INFO,
                "Info message",
                Instant.now(),
                null, null, null
        );

        List<AlertRule> matchingRules = alertRuleService.getMatchingRules(infoLog);

        // Should not match any rule
        assertThat(matchingRules).isEmpty();
    }

    @Test
    void getMatchingRules_warnSeverityDoesNotMatchErrorRules() {
        LogEvent warnLog = new LogEvent(
                "test-service",
                LogEvent.Severity.WARN,
                "Warning message",
                Instant.now(),
                null, null, null
        );

        List<AlertRule> matchingRules = alertRuleService.getMatchingRules(warnLog);

        // Should not match error-only rules
        assertThat(matchingRules).isEmpty();
    }

    @Test
    void getCooldownSeconds_returnsConfiguredValue() {
        assertThat(alertRuleService.getCooldownSeconds()).isEqualTo(60);
    }
}
