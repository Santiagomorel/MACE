package com.company.rotations.verification.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.company.rotations.verification.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VerificationCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCacheService.class);

    private final Cache<String, VerificationResult> cache;

    public VerificationCacheService(Cache<String, VerificationResult> verificationResultCache) {
        this.cache = verificationResultCache;
    }

    public VerificationResult get(String key) {
        if (key == null) {
            return null;
        }

        VerificationResult result = cache.getIfPresent(key);
        if (result != null) {
            logger.debug("Cache hit for key: {}", key);
        } else {
            logger.debug("Cache miss for key: {}", key);
        }
        return result;
    }

    public void put(String key, VerificationResult result) {
        if (key != null && result != null) {
            cache.put(key, result);
            logger.debug("Cached verification result for key: {}, status: {}",
                    key, result.getStatus());
        }
    }

    public void evict(String key) {
        if (key != null) {
            cache.invalidate(key);
            logger.debug("Evicted cache entry for key: {}", key);
        }
    }

    public void clear() {
        cache.invalidateAll();
        logger.info("Verification result cache cleared");
    }

    public long size() {
        return cache.estimatedSize();
    }

    public long hits() {
        return cache.stats().hitCount();
    }

    public long misses() {
        return cache.stats().missCount();
    }
}
