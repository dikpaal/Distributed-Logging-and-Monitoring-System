package com.logging.monitoring.repository;

import com.logging.monitoring.entity.LogEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class LogSpecification {

    public static Specification<LogEntity> hasServiceName(String serviceName) {
        return (root, query, cb) -> {
            if (serviceName == null || serviceName.isBlank()) {
                return null;
            }
            return cb.equal(root.get("serviceName"), serviceName);
        };
    }

    public static Specification<LogEntity> hasSeverity(String severity) {
        return (root, query, cb) -> {
            if (severity == null || severity.isBlank()) {
                return null;
            }
            return cb.equal(root.get("severity"), severity.toUpperCase());
        };
    }

    public static Specification<LogEntity> hasTraceId(String traceId) {
        return (root, query, cb) -> {
            if (traceId == null || traceId.isBlank()) {
                return null;
            }
            return cb.equal(root.get("traceId"), traceId);
        };
    }

    public static Specification<LogEntity> timestampAfter(Instant startTime) {
        return (root, query, cb) -> {
            if (startTime == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.get("timestamp"), startTime);
        };
    }

    public static Specification<LogEntity> timestampBefore(Instant endTime) {
        return (root, query, cb) -> {
            if (endTime == null) {
                return null;
            }
            return cb.lessThanOrEqualTo(root.get("timestamp"), endTime);
        };
    }
}
