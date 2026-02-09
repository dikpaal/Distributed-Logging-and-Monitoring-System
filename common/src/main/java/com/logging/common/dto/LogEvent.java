package com.logging.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record LogEvent(
    @NotBlank(message = "serviceName is required")
    String serviceName,

    @NotNull(message = "severity is required")
    Severity severity,

    @NotBlank(message = "message is required")
    String message,

    Instant timestamp,

    String traceId,

    String host,

    Map<String, Object> metadata
) {
    public enum Severity {
        INFO, WARN, ERROR
    }

    public LogEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
