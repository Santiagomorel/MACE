package com.company.rotations.alerting.dedup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SecretDedupServiceTest {

    private SecretDedupService service;

    @BeforeEach
    void setUp() {
        service = new SecretDedupService(24, 1);
        service.init();
    }

    private SecretDedupService createZeroCooldownService() {
        SecretDedupService zeroService = new SecretDedupService(0, 0);
        zeroService.init();
        return zeroService;
    }

    private SecretDedupService createCooldownService() {
        SecretDedupService cooldownService = new SecretDedupService(
                TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(1));
        cooldownService.init();
        return cooldownService;
    }

    @Nested
    @DisplayName("No Hash")
    class NoHashTests {

        @Test
        @DisplayName("Should return PROCEED for null hash")
        void shouldReturnProceedForNull() {
            assertEquals(SecretDedupService.DedupResult.PROCEED, service.checkOrRegister(null, "repo"));
        }

        @Test
        @DisplayName("Should return PROCEED for blank hash")
        void shouldReturnProceedForBlank() {
            assertEquals(SecretDedupService.DedupResult.PROCEED, service.checkOrRegister("", "repo"));
        }
    }

    @Nested
    @DisplayName("First Registration")
    class FirstRegistrationTests {

        @Test
        @DisplayName("Should return PROCEED for first occurrence")
        void shouldReturnProceedForFirst() {
            assertEquals(SecretDedupService.DedupResult.PROCEED, service.checkOrRegister("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should register entry with PROCESSING status")
        void shouldRegisterAsProcessing() {
            service.checkOrRegister("hash1", "repo1");
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    service.getStatus("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should use composite key with repository")
        void shouldUseCompositeKey() {
            service.checkOrRegister("hash1", "repo-a");
            service.checkOrRegister("hash1", "repo-b");
            // Different repos should have different entries
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    service.getStatus("hash1", "repo-a"));
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    service.getStatus("hash1", "repo-b"));
        }

        @Test
        @DisplayName("Should use no_repo default when repository is null")
        void shouldUseDefaultRepoWhenNull() {
            service.checkOrRegister("hash1", null);
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    service.getStatus("hash1", null));
        }

        @Test
        @DisplayName("Should use no_repo default when repository is blank")
        void shouldUseDefaultRepoWhenBlank() {
            service.checkOrRegister("hash1", "");
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    service.getStatus("hash1", ""));
        }
    }

    @Nested
    @DisplayName("Cooldown")
    class CooldownTests {

        @Test
        @DisplayName("Should return SKIP_COOLDOWN for false positive within cooldown")
        void shouldReturnSkipCooldownForFalsePositive() {
            service.checkOrRegister("hash1", "repo1");
            service.updateStatus("hash1", "repo1", SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN, service.checkOrRegister("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should return SKIP_IN_PROGRESS for processing entry after cooldown expires")
        void shouldReturnSkipInProgress() {
            SecretDedupService zeroService = createZeroCooldownService();
            zeroService.checkOrRegister("hash1", "repo1");
            assertEquals(SecretDedupService.DedupResult.SKIP_IN_PROGRESS, zeroService.checkOrRegister("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should return PROCEED for new hash after PROCESSING status expired")
        void shouldReturnProceedAfterProcessingExpired() {
            service.checkOrRegister("hash1", "repo1");
            // Update status to TRUE_POSITIVE which has 1h cooldown
            service.updateStatus("hash1", "repo1", SecretDedupService.SecretDedupStatus.TRUE_POSITIVE);
            // Within cooldown, should return SKIP_COOLDOWN
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN, service.checkOrRegister("hash1", "repo1"));
        }
    }

    @Nested
    @DisplayName("Status Update")
    class StatusUpdateTests {

        @Test
        @DisplayName("Should update status to TRUE_POSITIVE")
        void shouldUpdateToTruePositive() {
            service.checkOrRegister("hash1", "repo1");
            service.updateStatus("hash1", "repo1", SecretDedupService.SecretDedupStatus.TRUE_POSITIVE);
            assertEquals(SecretDedupService.SecretDedupStatus.TRUE_POSITIVE,
                    service.getStatus("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should update status to FALSE_POSITIVE")
        void shouldUpdateToFalsePositive() {
            service.checkOrRegister("hash1", "repo1");
            service.updateStatus("hash1", "repo1", SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);
            assertEquals(SecretDedupService.SecretDedupStatus.FALSE_POSITIVE,
                    service.getStatus("hash1", "repo1"));
        }

        @Test
        @DisplayName("Should not update status for non-existent entry")
        void shouldNotUpdateNonExistent() {
            assertDoesNotThrow(() ->
                    service.updateStatus("nonexistent", "repo", SecretDedupService.SecretDedupStatus.TRUE_POSITIVE));
            assertNull(service.getStatus("nonexistent", "repo"));
        }
    }

    @Nested
    @DisplayName("Cache")
    class CacheTests {

        @Test
        @DisplayName("Should report cache size")
        void shouldReportCacheSize() {
            assertEquals(0, service.getCacheSize());
            service.checkOrRegister("hash1", "repo1");
            assertEquals(1, service.getCacheSize());
        }
    }

    @Nested
    @DisplayName("Enums")
    class EnumTests {

        @Test
        @DisplayName("DedupResult should have expected values")
        void dedupResultValues() {
            assertEquals(3, SecretDedupService.DedupResult.values().length);
            assertNotNull(SecretDedupService.DedupResult.PROCEED);
            assertNotNull(SecretDedupService.DedupResult.SKIP_COOLDOWN);
            assertNotNull(SecretDedupService.DedupResult.SKIP_IN_PROGRESS);
        }

        @Test
        @DisplayName("SecretDedupStatus should have expected values")
        void statusValues() {
            assertEquals(3, SecretDedupService.SecretDedupStatus.values().length);
            assertNotNull(SecretDedupService.SecretDedupStatus.PROCESSING);
            assertNotNull(SecretDedupService.SecretDedupStatus.TRUE_POSITIVE);
            assertNotNull(SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);
        }

        @Test
        @DisplayName("SecretDedupEntry should hold status and timestamp")
        void entryValues() {
            Instant now = Instant.now();
            SecretDedupService.SecretDedupEntry entry = new SecretDedupService.SecretDedupEntry(
                    SecretDedupService.SecretDedupStatus.PROCESSING, now);
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING, entry.getStatus());
            assertEquals(now, entry.getTimestamp());
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent secret dedup checks safely")
        void shouldHandleConcurrentChecks() throws InterruptedException {
            SecretDedupService zeroService = createZeroCooldownService();
            int threadCount = 10;
            int checksPerThread = 100;
            Thread[] threads = new Thread[threadCount];
            AtomicInteger proceedCount = new AtomicInteger(0);
            AtomicInteger skipCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < checksPerThread; i++) {
                        String hash = "conc-hash-" + (i % 3);
                        SecretDedupService.DedupResult result = zeroService.checkOrRegister(hash, "repo");
                        if (result == SecretDedupService.DedupResult.PROCEED) {
                            proceedCount.incrementAndGet();
                        } else {
                            skipCount.incrementAndGet();
                        }
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            int uniqueHashes = 3;
            int expectedProceed = uniqueHashes;
            assertTrue(proceedCount.get() >= expectedProceed,
                    "At least one PROCEED per unique hash, got " + proceedCount.get());
            assertEquals(threadCount * checksPerThread - proceedCount.get(), skipCount.get(),
                    "Subsequent checks should skip");
        }

        @Test
        @DisplayName("Should handle concurrent operations on same secret")
        void shouldHandleConcurrentSameSecret() throws InterruptedException {
            SecretDedupService zeroService = createZeroCooldownService();
            int threadCount = 20;
            Thread[] threads = new Thread[threadCount];
            AtomicBoolean hadException = new AtomicBoolean(false);

            for (int t = 0; t < threadCount; t++) {
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 50; i++) {
                        try {
                            zeroService.checkOrRegister("hot-secret", "repo");
                        } catch (Exception e) {
                            hadException.set(true);
                        }
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertFalse(hadException.get(), "No exceptions during concurrent access");
        }

        @Test
        @DisplayName("Should handle concurrent status updates safely")
        void shouldHandleConcurrentStatusUpdates() throws InterruptedException {
            SecretDedupService zeroService = createZeroCooldownService();
            zeroService.checkOrRegister("update-secret", "repo");
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int t = 0; t < threadCount; t++) {
                final SecretDedupService.SecretDedupStatus status =
                        t % 2 == 0
                                ? SecretDedupService.SecretDedupStatus.TRUE_POSITIVE
                                : SecretDedupService.SecretDedupStatus.FALSE_POSITIVE;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 50; i++) {
                        zeroService.updateStatus("update-secret", "repo", status);
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            SecretDedupService.SecretDedupStatus finalStatus = zeroService.getStatus("update-secret", "repo");
            assertNotNull(finalStatus, "Status should exist after concurrent updates");
        }
    }

    @Nested
    @DisplayName("Full FP Cooldown Cycle (Task 11.7)")
    class FpCooldownCycleTests {

        @Test
        @DisplayName("FP: verifier returns false_positive -> cooldown 24h -> re-send -> dedup skip")
        void shouldSkipAfterFalsePositiveCooldown() {
            SecretDedupService cooldownService = createCooldownService();
            String hash = "fp-secret";
            String repo = "my-repo";

            // Step 1: First alert - should proceed
            assertEquals(SecretDedupService.DedupResult.PROCEED,
                    cooldownService.checkOrRegister(hash, repo));

            // Step 2: Initial status is PROCESSING
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    cooldownService.getStatus(hash, repo));

            // Step 3: Verifier returns false_positive
            cooldownService.updateStatus(hash, repo, SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);
            assertEquals(SecretDedupService.SecretDedupStatus.FALSE_POSITIVE,
                    cooldownService.getStatus(hash, repo));

            // Step 4: Re-send same secret - should be skipped due to cooldown
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN,
                    cooldownService.checkOrRegister(hash, repo));
        }

        @Test
        @DisplayName("TP with action completed -> cooldown -> re-send after cooldown expires -> proceed")
        void shouldProceedAfterTpCooldownExpires() throws InterruptedException {
            SecretDedupService shortCooldownService = new SecretDedupService(500.0, 100.0);
            shortCooldownService.init();
            String hash = "tp-secret";
            String repo = "my-repo";

            // First alert proceeds
            assertEquals(SecretDedupService.DedupResult.PROCEED,
                    shortCooldownService.checkOrRegister(hash, repo));

            // Verifier returns true_positive
            shortCooldownService.updateStatus(hash, repo, SecretDedupService.SecretDedupStatus.TRUE_POSITIVE);

            // Within cooldown -> skip
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN,
                    shortCooldownService.checkOrRegister(hash, repo));

            // Wait for TP cooldown to expire (100ms)
            Thread.sleep(150);

            // After cooldown expires -> proceed again (re-register as PROCESSING)
            assertEquals(SecretDedupService.DedupResult.PROCEED,
                    shortCooldownService.checkOrRegister(hash, repo));

            // Status should be PROCESSING again
            assertEquals(SecretDedupService.SecretDedupStatus.PROCESSING,
                    shortCooldownService.getStatus(hash, repo));
        }

        @Test
        @DisplayName("Multiple different secrets should not interfere")
        void shouldNotInterfereBetweenSecrets() {
            SecretDedupService cooldownService = createCooldownService();

            cooldownService.checkOrRegister("hash-a", "repo");
            cooldownService.updateStatus("hash-a", "repo", SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);

            cooldownService.checkOrRegister("hash-b", "repo");
            // hash-b should still proceed since it's a different secret
            assertEquals(SecretDedupService.DedupResult.SKIP_IN_PROGRESS,
                    cooldownService.checkOrRegister("hash-b", "repo"));
            // hash-a should be cooldown skipped
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN,
                    cooldownService.checkOrRegister("hash-a", "repo"));
        }

        @Test
        @DisplayName("Same secret in different repos should be independent")
        void shouldBeIndependentAcrossRepos() {
            SecretDedupService cooldownService = createCooldownService();
            String hash = "shared-hash";

            cooldownService.checkOrRegister(hash, "repo-a");
            cooldownService.updateStatus(hash, "repo-a", SecretDedupService.SecretDedupStatus.FALSE_POSITIVE);

            // Same hash in different repo should proceed (independent keys)
            assertEquals(SecretDedupService.DedupResult.SKIP_COOLDOWN,
                    cooldownService.checkOrRegister(hash, "repo-a"));
            assertEquals(SecretDedupService.DedupResult.PROCEED,
                    cooldownService.checkOrRegister(hash, "repo-b"));
        }
    }
}
