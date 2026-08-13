package com.company.rotations.alerting.dedup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

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
}
