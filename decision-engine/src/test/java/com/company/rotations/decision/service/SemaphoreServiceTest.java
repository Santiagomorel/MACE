package com.company.rotations.decision.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemaphoreServiceTest {

    private final SemaphoreService semaphore = new SemaphoreService();

    @Test
    void acquireSemaphore_returnsTrueFirstTime() {
        assertTrue(semaphore.acquireSemaphore("test-key", 900, 30));
    }

    @Test
    void acquireSemaphore_returnsFalseIfAlreadyHeld() {
        semaphore.acquireSemaphore("test-key", 900, 30);
        assertFalse(semaphore.acquireSemaphore("test-key", 900, 30));
    }

    @Test
    void acquireSemaphore_returnsTrueAfterRelease() {
        semaphore.acquireSemaphore("test-key", 900, 30);
        semaphore.releaseSemaphore("test-key");
        assertTrue(semaphore.acquireSemaphore("test-key", 900, 30));
    }

    @Test
    void acquireSemaphore_differentKeysIndependent() {
        assertTrue(semaphore.acquireSemaphore("key1", 900, 30));
        assertTrue(semaphore.acquireSemaphore("key2", 900, 30));
    }

    @Test
    void semaphoreExpiresAfterTtl() throws InterruptedException {
        semaphore.acquireSemaphore("test-key", 1, 30);
        Thread.sleep(1100); // Wait for TTL to expire
        assertTrue(semaphore.acquireSemaphore("test-key", 1, 30));
    }
}
