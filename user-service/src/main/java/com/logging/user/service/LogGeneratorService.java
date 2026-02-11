package com.logging.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LogGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LogGeneratorService.class);

    private final UserService userService;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    @Value("${app.generator.enabled:false}")
    private boolean autoStart;

    public LogGeneratorService(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(fixedRateString = "${app.generator.interval-ms:1000}")
    public void generateLogs() {
        if (!enabled.get()) {
            return;
        }

        try {
            String traceId = userService.generateTraceId();
            userService.simulateUserActivity(traceId);
            successCount.incrementAndGet();
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.debug("Log generation cycle completed with error: {}", e.getMessage());
        }
    }

    public void start() {
        enabled.set(true);
        log.info("Log generator started");
    }

    public void stop() {
        enabled.set(false);
        log.info("Log generator stopped");
    }

    public boolean isRunning() {
        return enabled.get();
    }

    public long getSuccessCount() {
        return successCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public void resetCounters() {
        successCount.set(0);
        errorCount.set(0);
    }
}
