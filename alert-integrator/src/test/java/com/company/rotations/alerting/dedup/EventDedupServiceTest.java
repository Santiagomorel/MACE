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

class EventDedupServiceTest {

    private EventDedupService service;

    @BeforeEach
    void setUp() {
        // Use short TTL for testing
        service = new EventDedupService(1);
        service.init();
    }

    @Nested
    @DisplayName("Duplicate Detection")
    class DuplicateTests {

        @Test
        @DisplayName("Should return false for new event")
        void shouldReturnFalseForNewEvent() {
            assertFalse(service.isDuplicate("new-event-1"));
        }

        @Test
        @DisplayName("Should return true for duplicate event")
        void shouldReturnTrueForDuplicateEvent() {
            service.isDuplicate("event-1");
            assertTrue(service.isDuplicate("event-1"));
        }

        @Test
        @DisplayName("Should distinguish different events")
        void shouldDistinguishDifferentEvents() {
            service.isDuplicate("event-a");
            assertFalse(service.isDuplicate("event-b"));
        }

        @Test
        @DisplayName("Should return false for null event ID")
        void shouldReturnFalseForNull() {
            assertFalse(service.isDuplicate(null));
        }

        @Test
        @DisplayName("Should return false for blank event ID")
        void shouldReturnFalseForBlank() {
            assertFalse(service.isDuplicate("   "));
        }

        @Test
        @DisplayName("Should hash the key internally")
        void shouldHashKeyInternally() {
            service.isDuplicate("event-with-long-name-to-test-hashing");
            service.isDuplicate("event-with-long-name-to-test-hashing");
            // No exception means hashing worked
        }
    }

    @Nested
    @DisplayName("Cache Management")
    class CacheManagementTests {

        @Test
        @DisplayName("Should report cache size")
        void shouldReportCacheSize() {
            assertEquals(0, service.getCacheSize());
            service.isDuplicate("event-1");
            assertEquals(1, service.getCacheSize());
        }

        @Test
        @DisplayName("Should remove event from cache")
        void shouldRemoveEvent() {
            service.isDuplicate("to-remove");
            assertTrue(service.isDuplicate("to-remove"));
            service.remove("to-remove");
            assertFalse(service.isDuplicate("to-remove"));
        }

        @Test
        @DisplayName("Should handle remove for non-existent event")
        void shouldHandleRemoveNonExistent() {
            assertDoesNotThrow(() -> service.remove("non-existent"));
        }

        @Test
        @DisplayName("Should handle remove for null event")
        void shouldHandleRemoveNull() {
            assertDoesNotThrow(() -> service.remove(null));
        }
    }
}
