package com.company.rotations.decision.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SemaphoreService {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreService.class);

    private final ConcurrentHashMap<String, SemaphoreState> semaphores = new ConcurrentHashMap<>();

    public boolean acquireSemaphore(String key, long ttlSeconds, long waitSeconds) {
        SemaphoreState state = semaphores.get(key);
        long now = System.currentTimeMillis();

        if (state != null && !state.isExpired(now, ttlSeconds)) {
            long elapsed = now - state.getAcquiredAt();
            if (elapsed <= waitSeconds * 1000) {
                log.debug("Semaphore {} already held, waiting for next cycle", key);
                return false;
            }
        }

        SemaphoreState newState = new SemaphoreState(now);
        SemaphoreState previous = semaphores.put(key, newState);
        if (previous != null && !previous.isExpired(now, ttlSeconds)) {
            log.debug("Semaphore {} was held by another process, acquired it after TTL expired", key);
        }
        return true;
    }

    public void releaseSemaphore(String key) {
        semaphores.remove(key);
        log.debug("Released semaphore {}", key);
    }

    private static class SemaphoreState {
        private final long acquiredAt;

        public SemaphoreState(long acquiredAt) {
            this.acquiredAt = acquiredAt;
        }

        public long getAcquiredAt() { return acquiredAt; }

        public boolean isExpired(long now, long ttlSeconds) {
            return (now - acquiredAt) > (ttlSeconds * 1000);
        }
    }
}
