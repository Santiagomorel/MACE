package com.company.rotations.alerting.dedup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class EventDedupService {

    private static final Logger logger = LoggerFactory.getLogger(EventDedupService.class);

    private Cache<String, Instant> cache;
    private final long ttlSeconds;

    @Autowired
    public EventDedupService(
            @Value("${app.alerting.event-dedup-ttl-minutes:5}") long ttlMinutes) {
        this.ttlSeconds = ttlMinutes * 60;
    }

    public EventDedupService(double ttlSeconds) {
        this.ttlSeconds = (long) ttlSeconds;
    }

    @PostConstruct
    public void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(100_000)
                .recordStats()
                .build();
        logger.info("EventDedupService initialized with TTL={} seconds, maxCacheSize=100000", ttlSeconds);
    }

    public boolean isDuplicate(String sourceEventId) {
        if (sourceEventId == null || sourceEventId.isBlank()) {
            return false;
        }
        String key = hashKey(sourceEventId);
        Instant existingTimestamp = cache.getIfPresent(key);
        if (existingTimestamp != null) {
            logger.debug("Event dedup hit for sourceEventId={}, TTL remaining={}s",
                    sourceEventId, ttlSeconds - java.time.Duration.between(existingTimestamp, Instant.now()).toSeconds());
            return true;
        }
        cache.put(key, Instant.now());
        logger.debug("Event dedup miss for sourceEventId={}, storing with TTL={}s",
                sourceEventId, ttlSeconds);
        return false;
    }

    public void remove(String sourceEventId) {
        if (sourceEventId != null) {
            cache.invalidate(hashKey(sourceEventId));
        }
    }

    public long getCacheSize() {
        return cache.estimatedSize();
    }

    private String hashKey(String sourceEventId) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sourceEventId.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return sourceEventId;
        }
    }
}
