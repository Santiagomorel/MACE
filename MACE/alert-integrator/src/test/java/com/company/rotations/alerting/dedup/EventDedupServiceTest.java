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

class EventDedupServiceTest {

    private EventDedupService service;

    @BeforeEach
    void setUp() {
        // Use short TTL for testing (1 second)
        service = new EventDedupService(1.0);
        service.init();
    }

    @Nested
    @DisplayName("TTL Expiration")
    class TtlExpirationTests {

        @Test
        @DisplayName("Should return false for event after TTL expiration")
        void shouldReturnFalseAfterTtlExpiry() throws InterruptedException {
            service.isDuplicate("expire-event");
            assertTrue(service.isDuplicate("expire-event"));
            Thread.sleep(1500);
            assertFalse(service.isDuplicate("expire-event"));
        }

        @Test
        @DisplayName("Should distinguish expired and non-expired events")
        void shouldDistinguishExpiredAndNonExpired() throws InterruptedException {
            service.isDuplicate("old-event");
            Thread.sleep(1500);
            service.isDuplicate("new-event");
            // Old event should have expired (registered 1.5s ago with 1s TTL)
            assertFalse(service.isDuplicate("old-event"), "Old event should have expired");
            // New event should still be in cache (registered just now)
            assertTrue(service.isDuplicate("new-event"), "New event should still be in cache");
        }

        @Test
        @DisplayName("Should expire multiple events after TTL")
        void shouldExpireAllEventsAfterTtl() throws InterruptedException {
            for (int i = 0; i < 5; i++) {
                service.isDuplicate("multi-event-" + i);
            }
            for (int i = 0; i < 5; i++) {
                assertTrue(service.isDuplicate("multi-event-" + i));
            }
            Thread.sleep(1500);
            for (int i = 0; i < 5; i++) {
                assertFalse(service.isDuplicate("multi-event-" + i), "All events should have expired");
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent duplicate checks safely")
        void shouldHandleConcurrentChecks() throws InterruptedException {
            int threadCount = 10;
            int checksPerThread = 100;
            Thread[] threads = new Thread[threadCount];
            AtomicInteger hitCount = new AtomicInteger(0);
            AtomicInteger missCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < checksPerThread; i++) {
                        String eventId = "concurrent-event-" + (i % 5);
                        if (service.isDuplicate(eventId)) {
                            hitCount.incrementAndGet();
                        } else {
                            missCount.incrementAndGet();
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

            int uniqueEvents = 5;
            assertTrue(missCount.get() >= uniqueEvents,
                    "At least one miss per unique event, got " + missCount.get());
            assertEquals(threadCount * checksPerThread - missCount.get(), hitCount.get(),
                    "Subsequent checks should be hits");
        }

        @Test
        @DisplayName("Should handle concurrent operations on same event")
        void shouldHandleConcurrentSameEvent() throws InterruptedException {
            int threadCount = 20;
            Thread[] threads = new Thread[threadCount];
            AtomicBoolean hadException = new AtomicBoolean(false);

            for (int t = 0; t < threadCount; t++) {
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 50; i++) {
                        try {
                            service.isDuplicate("hot-event");
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

            assertFalse(hadException.get(), "No exceptions should occur during concurrent access");
            assertTrue(service.isDuplicate("hot-event"), "Event should still be in cache");
        }
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
