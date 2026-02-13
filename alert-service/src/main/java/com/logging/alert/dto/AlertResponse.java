package com.logging.alert.dto;

import com.logging.alert.entity.AlertEntity;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        String ruleName,
        String serviceName,
        String severity,
        Long count,
        Long threshold,
        Integer windowSeconds,
        String message,
        Instant triggeredAt,
        Instant createdAt
) {
    public static AlertResponse from(AlertEntity entity) {
        return new AlertResponse(
                entity.getId(),
                entity.getRuleName(),
                entity.getServiceName(),
                entity.getSeverity(),
                entity.getCount(),
                entity.getThreshold(),
                entity.getWindowSeconds(),
                entity.getMessage(),
                entity.getTriggeredAt(),
                entity.getCreatedAt()
        );
    }
}
