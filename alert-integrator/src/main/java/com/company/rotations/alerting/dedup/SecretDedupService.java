package com.company.rotations.alerting.dedup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class SecretDedupService {

    private static final Logger logger = LoggerFactory.getLogger(SecretDedupService.class);

    private Cache<String, SecretDedupEntry> cache;
    private final long fpCooldownMillis;
    private final long tpCooldownMillis;

    public SecretDedupService(
            @Value("${app.alerting.secret-dedup.false-positive-cooldown-hours:24}") long fpCooldownHours,
            @Value("${app.alerting.secret-dedup.true-positive-cooldown-hours:1}") long tpCooldownHours) {
        this.fpCooldownMillis = TimeUnit.HOURS.toMillis(fpCooldownHours);
        this.tpCooldownMillis = TimeUnit.HOURS.toMillis(tpCooldownHours);
    }

    @PostConstruct
    public void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(500_000)
                .recordStats()
                .build();
        logger.info("SecretDedupService initialized with FP cooldown={}h, TP cooldown={}h",
                fpCooldownMillis / 3600000, tpCooldownMillis / 3600000);
    }

    public DedupResult checkOrRegister(String valueHash, String repository) {
        if (valueHash == null || valueHash.isBlank()) {
            return DedupResult.PROCEED;
        }
        String key = compositeKey(valueHash, repository);
        SecretDedupEntry existing = cache.getIfPresent(key);

        if (existing == null) {
            cache.put(key, new SecretDedupEntry(SecretDedupStatus.PROCESSING, Instant.now()));
            logger.debug("Secret dedup miss: valueHash={}, repo={}", truncate(valueHash), repository);
            return DedupResult.PROCEED;
        }

        long elapsed = java.time.Duration.between(existing.getTimestamp(), Instant.now()).toMillis();
        long cooldown = existing.getStatus() == SecretDedupStatus.FALSE_POSITIVE
                ? fpCooldownMillis : tpCooldownMillis;

        if (elapsed < cooldown) {
            logger.debug("Secret dedup hit (cooldown): status={}, elapsed={}ms, cooldown={}ms",
                    existing.getStatus(), elapsed, cooldown);
            return DedupResult.SKIP_COOLDOWN;
        }

        if (existing.getStatus() == SecretDedupStatus.PROCESSING) {
            logger.debug("Secret dedup hit (in_progress): valueHash={}, repo={}",
                    truncate(valueHash), repository);
            return DedupResult.SKIP_IN_PROGRESS;
        }

        cache.put(key, new SecretDedupEntry(SecretDedupStatus.PROCESSING, Instant.now()));
        logger.debug("Secret dedup: cooldown expired, re-registering as processing");
        return DedupResult.PROCEED;
    }

    public void updateStatus(String valueHash, String repository, SecretDedupStatus newStatus) {
        String key = compositeKey(valueHash, repository);
        SecretDedupEntry existing = cache.getIfPresent(key);
        if (existing != null) {
            cache.put(key, new SecretDedupEntry(newStatus, Instant.now()));
            logger.debug("Secret dedup status updated: key={}, newStatus={}", truncate(valueHash), newStatus);
        }
    }

    public SecretDedupStatus getStatus(String valueHash, String repository) {
        String key = compositeKey(valueHash, repository);
        SecretDedupEntry entry = cache.getIfPresent(key);
        return entry != null ? entry.getStatus() : null;
    }

    public long getCacheSize() {
        return cache.estimatedSize();
    }

    private String compositeKey(String valueHash, String repository) {
        String repo = (repository != null && !repository.isBlank()) ? repository : "__no_repo__";
        return valueHash + ":" + repo;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 16) return value;
        return value.substring(0, 8) + "..." + value.substring(value.length() - 8);
    }

    public enum DedupResult {
        PROCEED,
        SKIP_COOLDOWN,
        SKIP_IN_PROGRESS
    }

    public enum SecretDedupStatus {
        PROCESSING,
        TRUE_POSITIVE,
        FALSE_POSITIVE
    }

    public static class SecretDedupEntry {
        private final SecretDedupStatus status;
        private final Instant timestamp;

        public SecretDedupEntry(SecretDedupStatus status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }

        public SecretDedupStatus getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
    }
}
