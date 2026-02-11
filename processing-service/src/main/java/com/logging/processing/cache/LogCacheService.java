package com.logging.processing.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logging.processing.entity.LogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LogCacheService {

    private static final Logger log = LoggerFactory.getLogger(LogCacheService.class);
    private static final String KEY_PREFIX = "logs:recent:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int recentLogsLimit;
    private final Duration ttl;

    public LogCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.recent-logs-limit}") int recentLogsLimit,
            @Value("${app.cache.ttl-hours}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.recentLogsLimit = recentLogsLimit;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public void cacheRecentLog(LogEntity logEntity) {
        String key = KEY_PREFIX + logEntity.getServiceName();

        try {
            String logJson = objectMapper.writeValueAsString(logEntity);

            // Push to left (most recent first)
            redisTemplate.opsForList().leftPush(key, logJson);

            // Trim to keep only recent logs
            redisTemplate.opsForList().trim(key, 0, recentLogsLimit - 1);

            // Set TTL
            redisTemplate.expire(key, ttl);

            log.debug("Cached log for service: {}", logEntity.getServiceName());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize log for caching: {}", e.getMessage());
        }
    }
}
