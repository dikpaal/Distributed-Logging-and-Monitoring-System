package com.logging.ingestion.controller;

import com.logging.common.dto.LogEvent;
import com.logging.ingestion.kafka.LogProducer;
import com.logging.ingestion.service.IdempotencyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionController.class);

    private final LogProducer logProducer;
    private final IdempotencyService idempotencyService;

    public LogIngestionController(LogProducer logProducer, IdempotencyService idempotencyService) {
        this.logProducer = logProducer;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> ingestLog(
            @Valid @RequestBody LogEvent logEvent,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        log.debug("Received log: service={}, severity={}, traceId={}, idempotencyKey={}",
                logEvent.serviceName(), logEvent.severity(), logEvent.traceId(), idempotencyKey);

        // Check idempotency - reject duplicates
        if (!idempotencyService.tryAcquire(idempotencyKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "duplicate",
                            "message", "Request with this idempotency key was already processed"
                    ));
        }

        logProducer.send(logEvent, idempotencyKey);

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @Valid @RequestBody List<LogEvent> logEvents,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        log.debug("Received batch of {} logs, idempotencyKey={}", logEvents.size(), idempotencyKey);

        // Check idempotency for batch request
        if (!idempotencyService.tryAcquire(idempotencyKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "duplicate",
                            "message", "Request with this idempotency key was already processed"
                    ));
        }

        logEvents.forEach(event -> logProducer.send(event, null));

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "count", logEvents.size()
        ));
    }
}
