package com.logging.payment.controller;

import com.logging.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.getOrDefault("userId", "unknown");
            double amount = request.containsKey("amount")
                    ? ((Number) request.get("amount")).doubleValue()
                    : 0.0;

            Map<String, Object> result = paymentService.processPayment(traceId, userId, amount);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
