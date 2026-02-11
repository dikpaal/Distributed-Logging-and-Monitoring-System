package com.logging.payment.service;

import com.logging.common.dto.LogEvent.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Random random = new Random();

    private final LogSender logSender;
    private final RestTemplate restTemplate;
    private final String orderServiceUrl;

    public PaymentService(
            LogSender logSender,
            RestTemplate restTemplate,
            @Value("${app.order-service.url}") String orderServiceUrl) {
        this.logSender = logSender;
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processPayment(String traceId, String userId, double amount) {
        log.info("Processing payment [traceId={}, userId={}, amount={}]", traceId, userId, amount);

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Log payment initiation
        logSender.sendLog(Severity.INFO, "Payment processing initiated",
                traceId, Map.of("paymentId", paymentId, "userId", userId, "amount", amount));

        // Simulate processing time
        simulateLatency();

        // Simulate payment validation failures (5% rate)
        if (random.nextInt(100) < 5) {
            logSender.sendLog(Severity.ERROR, "Payment validation failed: insufficient funds",
                    traceId, Map.of("paymentId", paymentId, "errorCode", "INSUFFICIENT_FUNDS"));
            throw new RuntimeException("Payment failed: insufficient funds");
        }

        // Simulate slow processing warning (15% rate)
        if (random.nextInt(100) < 15) {
            logSender.sendLog(Severity.WARN, "Payment processing slower than expected",
                    traceId, Map.of("paymentId", paymentId, "processingTime", "high"));
        }

        // Log payment success
        logSender.sendLog(Severity.INFO, "Payment processed successfully",
                traceId, Map.of("paymentId", paymentId, "status", "COMPLETED"));

        // Call order-service with trace ID propagation
        Map<String, Object> orderResult = callOrderService(traceId, userId, paymentId);

        return Map.of(
                "paymentId", paymentId,
                "status", "COMPLETED",
                "amount", amount,
                "order", orderResult
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOrderService(String traceId, String userId, String paymentId) {
        logSender.sendLog(Severity.INFO, "Calling order-service",
                traceId, Map.of("paymentId", paymentId, "targetService", "order-service"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Trace-Id", traceId);

            Map<String, String> body = Map.of(
                    "userId", userId,
                    "paymentId", paymentId
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    orderServiceUrl + "/api/v1/orders",
                    request,
                    Map.class
            );

            logSender.sendLog(Severity.INFO, "Order-service call completed",
                    traceId, Map.of("paymentId", paymentId, "responseStatus", response.getStatusCode().value()));

            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            logSender.sendLog(Severity.ERROR, "Order-service call failed: " + e.getMessage(),
                    traceId, Map.of("paymentId", paymentId, "error", e.getClass().getSimpleName()));
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }
    }

    private void simulateLatency() {
        try {
            // Random latency between 30-150ms
            Thread.sleep(30 + random.nextInt(120));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
