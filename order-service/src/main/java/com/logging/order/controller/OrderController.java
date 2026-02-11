package com.logging.order.controller;

import com.logging.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody Map<String, String> request) {
        try {
            String userId = request.getOrDefault("userId", "unknown");
            String paymentId = request.getOrDefault("paymentId", "unknown");

            Map<String, Object> result = orderService.processOrder(traceId, userId, paymentId);
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
