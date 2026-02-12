package com.logging.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "ingestion:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            @Value("${app.idempotency.ttl-hours:24}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Check if an idempotency key has already been processed.
     * If not processed, marks it as processed atomically.
     *
     * @param idempotencyKey the unique key for this request
     * @return true if this is a new key (not seen before), false if duplicate
     */
    public boolean tryAcquire(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // No idempotency key means always process
        }

        String key = KEY_PREFIX + idempotencyKey;

        // SETNX with expiration - returns true if key was set (new), false if existed
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);

        if (Boolean.TRUE.equals(isNew)) {
            log.debug("New idempotency key accepted: {}", idempotencyKey);
            return true;
        } else {
            log.info("Duplicate idempotency key rejected: {}", idempotencyKey);
            return false;
        }
    }

    /**
     * Check if an idempotency key exists (already processed).
     */
    public boolean exists(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
