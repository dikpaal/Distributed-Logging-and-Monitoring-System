package com.logging.alert.model;

public class AlertRule {
    private String name;
    private String serviceName;
    private String severity;
    private int threshold;
    private int windowSeconds;

    public AlertRule() {
    }

    public AlertRule(String name, String serviceName, String severity, int threshold, int windowSeconds) {
        this.name = name;
        this.serviceName = serviceName;
        this.severity = severity;
        this.threshold = threshold;
        this.windowSeconds = windowSeconds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public boolean matches(String serviceName, String severity) {
        boolean severityMatches = this.severity == null || this.severity.equalsIgnoreCase(severity);
        boolean serviceMatches = this.serviceName == null || this.serviceName.equalsIgnoreCase(serviceName);
        return severityMatches && serviceMatches;
    }

    public String getWindowKey(String logServiceName) {
        // For rules with specific serviceName, use that; otherwise use "ALL" for global rules
        String svc = this.serviceName != null ? this.serviceName : "ALL";
        String sev = this.severity != null ? this.severity : "ALL";
        return String.format("alert:window:%s:%s:%s", name, svc, sev);
    }
}
