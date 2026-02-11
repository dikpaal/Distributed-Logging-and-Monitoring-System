package com.logging.order.service;

import com.logging.common.dto.LogEvent.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Random random = new Random();

    private final LogSender logSender;

    public OrderService(LogSender logSender) {
        this.logSender = logSender;
    }

    public Map<String, Object> processOrder(String traceId, String userId, String paymentId) {
        log.info("Processing order [traceId={}, userId={}, paymentId={}]", traceId, userId, paymentId);

        // Simulate processing time
        simulateLatency();

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Log order creation start
        logSender.sendLog(Severity.INFO, "Order processing started",
                traceId, Map.of("orderId", orderId, "userId", userId, "paymentId", paymentId));

        // Simulate occasional failures (10% failure rate)
        if (random.nextInt(100) < 10) {
            logSender.sendLog(Severity.ERROR, "Order processing failed: inventory unavailable",
                    traceId, Map.of("orderId", orderId, "errorCode", "INVENTORY_ERROR"));
            throw new RuntimeException("Inventory unavailable for order " + orderId);
        }

        // Simulate warnings (20% of successful orders)
        if (random.nextInt(100) < 20) {
            logSender.sendLog(Severity.WARN, "Order processed with delayed shipping",
                    traceId, Map.of("orderId", orderId, "delayDays", random.nextInt(5) + 1));
        }

        // Log successful completion
        logSender.sendLog(Severity.INFO, "Order processing completed successfully",
                traceId, Map.of("orderId", orderId, "status", "CREATED"));

        return Map.of(
                "orderId", orderId,
                "status", "CREATED",
                "userId", userId,
                "paymentId", paymentId
        );
    }

    private void simulateLatency() {
        try {
            // Random latency between 50-200ms
            Thread.sleep(50 + random.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
