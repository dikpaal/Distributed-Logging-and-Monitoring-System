package com.logging.alert.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTest {

    @Test
    void matches_withBothSeverityAndService_matchesExact() {
        AlertRule rule = new AlertRule("test-rule", "order-service", "ERROR", 5, 30);

        assertThat(rule.matches("order-service", "ERROR")).isTrue();
        assertThat(rule.matches("order-service", "WARN")).isFalse();
        assertThat(rule.matches("user-service", "ERROR")).isFalse();
    }

    @Test
    void matches_withOnlySeverity_matchesAnyService() {
        AlertRule rule = new AlertRule("error-rule", null, "ERROR", 10, 60);

        assertThat(rule.matches("any-service", "ERROR")).isTrue();
        assertThat(rule.matches("another-service", "ERROR")).isTrue();
        assertThat(rule.matches("any-service", "INFO")).isFalse();
    }

    @Test
    void matches_withOnlyService_matchesAnySeverity() {
        AlertRule rule = new AlertRule("service-rule", "user-service", null, 100, 60);

        assertThat(rule.matches("user-service", "ERROR")).isTrue();
        assertThat(rule.matches("user-service", "INFO")).isTrue();
        assertThat(rule.matches("user-service", "WARN")).isTrue();
        assertThat(rule.matches("other-service", "ERROR")).isFalse();
    }

    @Test
    void matches_withNoConstraints_matchesEverything() {
        AlertRule rule = new AlertRule("catch-all", null, null, 1000, 300);

        assertThat(rule.matches("any-service", "ERROR")).isTrue();
        assertThat(rule.matches("any-service", "INFO")).isTrue();
        assertThat(rule.matches("another-service", "WARN")).isTrue();
    }

    @Test
    void matches_caseInsensitive() {
        AlertRule rule = new AlertRule("test-rule", "Order-Service", "error", 5, 30);

        assertThat(rule.matches("order-service", "ERROR")).isTrue();
        assertThat(rule.matches("ORDER-SERVICE", "error")).isTrue();
    }

    @Test
    void getWindowKey_withSpecificService() {
        AlertRule rule = new AlertRule("service-down", "order-service", "ERROR", 5, 30);

        String key = rule.getWindowKey("any-service");

        // Should use the rule's service name, not the passed one
        assertThat(key).isEqualTo("alert:window:service-down:order-service:ERROR");
    }

    @Test
    void getWindowKey_withNoServiceConstraint() {
        AlertRule rule = new AlertRule("high-error-rate", null, "ERROR", 10, 60);

        String key = rule.getWindowKey("user-service");

        // Should use "ALL" for global rules (no serviceName constraint)
        assertThat(key).isEqualTo("alert:window:high-error-rate:ALL:ERROR");
    }

    @Test
    void getWindowKey_withNoSeverityConstraint() {
        AlertRule rule = new AlertRule("all-logs", "user-service", null, 100, 60);

        String key = rule.getWindowKey("user-service");

        assertThat(key).isEqualTo("alert:window:all-logs:user-service:ALL");
    }
}
