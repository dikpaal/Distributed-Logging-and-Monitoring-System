package com.logging.alert.config;

import com.logging.alert.model.AlertRule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.alert")
public class AlertProperties {
    private long evaluationIntervalMs = 5000;
    private int cooldownSeconds = 60;
    private List<AlertRule> rules = new ArrayList<>();

    public long getEvaluationIntervalMs() {
        return evaluationIntervalMs;
    }

    public void setEvaluationIntervalMs(long evaluationIntervalMs) {
        this.evaluationIntervalMs = evaluationIntervalMs;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public List<AlertRule> getRules() {
        return rules;
    }

    public void setRules(List<AlertRule> rules) {
        this.rules = rules;
    }
}
