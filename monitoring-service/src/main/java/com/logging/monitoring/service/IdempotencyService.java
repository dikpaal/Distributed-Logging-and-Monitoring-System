package com.logging.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "processed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            @Value("${app.idempotency.ttl-hours:24}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public boolean tryMarkAsProcessed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }

        String key = KEY_PREFIX + idempotencyKey;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);

        if (Boolean.TRUE.equals(isNew)) {
            log.debug("New message processed with idempotency key: {}", idempotencyKey);
            return true;
        } else {
            log.info("Skipping duplicate message with idempotency key: {}", idempotencyKey);
            return false;
        }
    }

    public void removeProcessedKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = KEY_PREFIX + idempotencyKey;
            redisTemplate.delete(key);
            log.debug("Removed processed key: {}", idempotencyKey);
        }
    }
}
