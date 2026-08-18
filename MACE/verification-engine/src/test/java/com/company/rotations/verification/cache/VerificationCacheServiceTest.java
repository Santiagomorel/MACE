package com.company.rotations.verification.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.company.rotations.verification.model.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("VerificationCacheService Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCacheServiceTest {

    @Mock
    private Cache<String, VerificationResult> cache;

    @Mock
    private VerificationResult cachedResult;

    private VerificationCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(cachedResult.getStatus()).thenReturn(com.company.rotations.verification.model.VerificationStatus.VERIFIED);
        when(cachedResult.getActionMatrix()).thenReturn(Collections.emptySet());
        cacheService = new VerificationCacheService(cache);
    }

    @Nested
    @DisplayName("Get operations")
    class GetTests {

        @Test
        @DisplayName("Should return cached result for valid key")
        void returnsCachedResult() {
            when(cache.getIfPresent("key-123")).thenReturn(cachedResult);

            VerificationResult result = cacheService.get("key-123");

            assertNotNull(result);
            assertEquals(cachedResult, result);
            verify(cache).getIfPresent("key-123");
        }

        @Test
        @DisplayName("Should return null for non-existent key")
        void returnsNullForMissingKey() {
            when(cache.getIfPresent("key-456")).thenReturn(null);

            VerificationResult result = cacheService.get("key-456");

            assertNull(result);
            verify(cache).getIfPresent("key-456");
        }

        @Test
        @DisplayName("Should return null for null key")
        void returnsNullForNullKey() {
            VerificationResult result = cacheService.get(null);

            assertNull(result);
            verify(cache, never()).getIfPresent(anyString());
        }
    }

    @Nested
    @DisplayName("Put operations")
    class PutTests {

        @Test
        @DisplayName("Should put valid key-value pair into cache")
        void putValidEntry() {
            VerificationResult result = VerificationResult.success(
                    "account-123", "arn:test", Set.of("s3:GetObject"), "2024-01-15");

            cacheService.put("key-123", result);

            verify(cache).put("key-123", result);
        }

        @Test
        @DisplayName("Should not put null key")
        void noPutForNullKey() {
            VerificationResult result = VerificationResult.success(
                    "account-123", "arn:test", Set.of("s3:GetObject"), "2024-01-15");

            cacheService.put(null, result);

            verify(cache, never()).put(anyString(), any());
        }

        @Test
        @DisplayName("Should not put null value")
        void noPutForNullValue() {
            cacheService.put("key-123", null);

            verify(cache, never()).put(anyString(), any());
        }

        @Test
        @DisplayName("Should not put with both null key and value")
        void noPutForBothNull() {
            cacheService.put(null, null);

            verify(cache, never()).put(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Eviction operations")
    class EvictionTests {

        @Test
        @DisplayName("Should evict entry for valid key")
        void evictValidEntry() {
            cacheService.evict("key-123");

            verify(cache).invalidate("key-123");
        }

        @Test
        @DisplayName("Should not evict for null key")
        void noEvictForNullKey() {
            cacheService.evict(null);

            verify(cache, never()).invalidate(anyString());
        }
    }

    @Nested
    @DisplayName("Clear operation")
    class ClearTests {

        @Test
        @DisplayName("Should clear all entries")
        void clearAllEntries() {
            cacheService.clear();

            verify(cache).invalidateAll();
        }
    }

    @Nested
    @DisplayName("Size and statistics")
    class SizeAndStatsTests {

        @Test
        @DisplayName("Should return cache estimated size")
        void returnsSize() {
            when(cache.estimatedSize()).thenReturn(42L);

            long size = cacheService.size();

            assertEquals(42L, size);
        }

        @Test
        @DisplayName("Should return hit count from stats")
        void returnsHits() {
            CacheStats stats = mock(CacheStats.class);
            when(stats.hitCount()).thenReturn(100L);
            when(cache.stats()).thenReturn(stats);

            long hits = cacheService.hits();

            assertEquals(100L, hits);
        }

        @Test
        @DisplayName("Should return miss count from stats")
        void returnsMisses() {
            CacheStats stats = mock(CacheStats.class);
            when(stats.missCount()).thenReturn(20L);
            when(cache.stats()).thenReturn(stats);

            long misses = cacheService.misses();

            assertEquals(20L, misses);
        }

        @Test
        @DisplayName("Should return zero hits when no stats recorded")
        void zeroHits() {
            CacheStats stats = mock(CacheStats.class);
            when(stats.hitCount()).thenReturn(0L);
            when(cache.stats()).thenReturn(stats);

            assertEquals(0L, cacheService.hits());
        }

        @Test
        @DisplayName("Should return zero misses when no stats recorded")
        void zeroMisses() {
            CacheStats stats = mock(CacheStats.class);
            when(stats.missCount()).thenReturn(0L);
            when(cache.stats()).thenReturn(stats);

            assertEquals(0L, cacheService.misses());
        }
    }
}
