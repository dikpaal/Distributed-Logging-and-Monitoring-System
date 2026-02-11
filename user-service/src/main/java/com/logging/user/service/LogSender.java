package com.logging.user.service;

import com.logging.common.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;

@Service
public class LogSender {

    private static final Logger log = LoggerFactory.getLogger(LogSender.class);

    private final RestTemplate restTemplate;
    private final String ingestionUrl;
    private final String hostname;

    public LogSender(
            RestTemplate restTemplate,
            @Value("${app.ingestion.url}") String ingestionUrl) {
        this.restTemplate = restTemplate;
        this.ingestionUrl = ingestionUrl;
        this.hostname = getHostname();
    }

    public void sendLog(LogEvent.Severity severity, String message, String traceId, Map<String, Object> metadata) {
        LogEvent logEvent = new LogEvent(
                "user-service",
                severity,
                message,
                Instant.now(),
                traceId,
                hostname,
                metadata
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LogEvent> request = new HttpEntity<>(logEvent, headers);

            restTemplate.postForEntity(ingestionUrl + "/api/v1/logs", request, String.class);
            log.debug("Sent log: {} [traceId={}]", message, traceId);
        } catch (Exception e) {
            log.error("Failed to send log: {}", e.getMessage());
        }
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
