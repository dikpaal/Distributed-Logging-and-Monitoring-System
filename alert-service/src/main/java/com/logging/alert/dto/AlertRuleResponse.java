package com.logging.alert.dto;

import com.logging.alert.model.AlertRule;

public record AlertRuleResponse(
        String name,
        String serviceName,
        String severity,
        int threshold,
        int windowSeconds
) {
    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getName(),
                rule.getServiceName(),
                rule.getSeverity(),
                rule.getThreshold(),
                rule.getWindowSeconds()
        );
    }
}
