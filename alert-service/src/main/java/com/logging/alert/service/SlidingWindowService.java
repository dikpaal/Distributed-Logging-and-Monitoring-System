package com.logging.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SlidingWindowService {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowService.class);
    private final StringRedisTemplate redisTemplate;
    private final ZSetOperations<String, String> zSetOps;

    public SlidingWindowService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOps = redisTemplate.opsForZSet();
    }

    /**
     * Add an entry to the sliding window for a rule.
     * Uses Redis sorted sets with timestamp as score.
     */
    public void addEntry(String windowKey, Instant timestamp) {
        double score = timestamp.toEpochMilli();
        String member = UUID.randomUUID().toString();
        zSetOps.add(windowKey, member, score);

        // Set expiration on the key (2x window size for safety)
        redisTemplate.expire(windowKey, 5, TimeUnit.MINUTES);

        log.debug("Added entry to window {}: score={}", windowKey, score);
    }

    /**
     * Get the count of entries within the sliding window.
     */
    public long getCount(String windowKey, int windowSeconds) {
        Instant now = Instant.now();
        long windowStart = now.minusSeconds(windowSeconds).toEpochMilli();
        long windowEnd = now.toEpochMilli();

        Long count = zSetOps.count(windowKey, windowStart, windowEnd);
        return count != null ? count : 0;
    }

    /**
     * Clean up old entries outside the window.
     */
    public void cleanupOldEntries(String windowKey, int windowSeconds) {
        long cutoff = Instant.now().minusSeconds(windowSeconds * 2L).toEpochMilli();
        Long removed = zSetOps.removeRangeByScore(windowKey, 0, cutoff);
        if (removed != null && removed > 0) {
            log.debug("Cleaned up {} old entries from {}", removed, windowKey);
        }
    }

    /**
     * Get all entries in a window (for debugging).
     */
    public Set<String> getEntries(String windowKey) {
        return zSetOps.range(windowKey, 0, -1);
    }
}
