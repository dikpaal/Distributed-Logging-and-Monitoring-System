package com.logging.monitoring.controller;

import com.logging.monitoring.dto.LogResponse;
import com.logging.monitoring.dto.PagedResponse;
import com.logging.monitoring.dto.ServiceLogCount;
import com.logging.monitoring.dto.SeverityCount;
import com.logging.monitoring.service.LogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/logs")
    public ResponseEntity<PagedResponse<LogResponse>> searchLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<LogResponse> response = logService.searchLogs(
                serviceName, severity, traceId, startTime, endTime, page, size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs/{id}")
    public ResponseEntity<LogResponse> getLogById(@PathVariable UUID id) {
        return logService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/logs/trace/{traceId}")
    public ResponseEntity<List<LogResponse>> getLogsByTraceId(@PathVariable String traceId) {
        List<LogResponse> logs = logService.getLogsByTraceId(traceId);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/metrics/counts")
    public ResponseEntity<List<SeverityCount>> getCountsBySeverity(
            @RequestParam(required = false) String serviceName
    ) {
        List<SeverityCount> counts;
        if (serviceName != null && !serviceName.isBlank()) {
            counts = logService.getCountsBySeverityForService(serviceName);
        } else {
            counts = logService.getCountsBySeverity();
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/metrics/services")
    public ResponseEntity<List<ServiceLogCount>> getCountsByService() {
        List<ServiceLogCount> counts = logService.getCountsByService();
        return ResponseEntity.ok(counts);
    }
}
