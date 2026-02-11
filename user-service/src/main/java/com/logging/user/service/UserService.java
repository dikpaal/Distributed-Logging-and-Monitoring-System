package com.logging.user.service;

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
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Random random = new Random();

    private final LogSender logSender;
    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public UserService(
            LogSender logSender,
            RestTemplate restTemplate,
            @Value("${app.payment-service.url}") String paymentServiceUrl) {
        this.logSender = logSender;
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> simulateUserActivity(String traceId) {
        String userId = "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Simulating user activity [traceId={}, userId={}]", traceId, userId);

        // Log user authentication
        logSender.sendLog(Severity.INFO, "User authentication started",
                traceId, Map.of("userId", userId, "authMethod", "password"));

        // Simulate auth latency
        simulateLatency(20, 80);

        // Simulate auth failures (8% rate)
        if (random.nextInt(100) < 8) {
            logSender.sendLog(Severity.ERROR, "Authentication failed: invalid credentials",
                    traceId, Map.of("userId", userId, "errorCode", "AUTH_FAILED"));
            throw new RuntimeException("Authentication failed for user " + userId);
        }

        // Simulate suspicious activity warning (12% rate)
        if (random.nextInt(100) < 12) {
            logSender.sendLog(Severity.WARN, "Suspicious login attempt detected",
                    traceId, Map.of("userId", userId, "reason", "unusual_location"));
        }

        // Log successful auth
        logSender.sendLog(Severity.INFO, "User authenticated successfully",
                traceId, Map.of("userId", userId, "sessionId", UUID.randomUUID().toString().substring(0, 8)));

        // Simulate some user action that triggers payment
        double amount = 10.0 + random.nextDouble() * 990.0; // $10 - $1000
        amount = Math.round(amount * 100.0) / 100.0;

        logSender.sendLog(Severity.INFO, "User initiated purchase",
                traceId, Map.of("userId", userId, "amount", amount));

        // Call payment-service with trace ID propagation
        Map<String, Object> paymentResult = callPaymentService(traceId, userId, amount);

        // Log completion
        logSender.sendLog(Severity.INFO, "User activity completed",
                traceId, Map.of("userId", userId, "result", "SUCCESS"));

        return Map.of(
                "traceId", traceId,
                "userId", userId,
                "amount", amount,
                "payment", paymentResult
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callPaymentService(String traceId, String userId, double amount) {
        logSender.sendLog(Severity.INFO, "Calling payment-service",
                traceId, Map.of("userId", userId, "targetService", "payment-service"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Trace-Id", traceId);

            Map<String, Object> body = Map.of(
                    "userId", userId,
                    "amount", amount
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/api/v1/payments",
                    request,
                    Map.class
            );

            logSender.sendLog(Severity.INFO, "Payment-service call completed",
                    traceId, Map.of("userId", userId, "responseStatus", response.getStatusCode().value()));

            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            logSender.sendLog(Severity.ERROR, "Payment-service call failed: " + e.getMessage(),
                    traceId, Map.of("userId", userId, "error", e.getClass().getSimpleName()));
            throw new RuntimeException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + random.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
