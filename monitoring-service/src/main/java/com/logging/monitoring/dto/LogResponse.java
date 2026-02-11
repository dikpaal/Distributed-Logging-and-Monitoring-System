package com.logging.monitoring.dto;

import com.logging.monitoring.entity.LogEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LogResponse(
        UUID id,
        String serviceName,
        String severity,
        String message,
        Instant timestamp,
        String traceId,
        String host,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static LogResponse from(LogEntity entity) {
        return new LogResponse(
                entity.getId(),
                entity.getServiceName(),
                entity.getSeverity(),
                entity.getMessage(),
                entity.getTimestamp(),
                entity.getTraceId(),
                entity.getHost(),
                entity.getMetadata(),
                entity.getCreatedAt()
        );
    }
}
