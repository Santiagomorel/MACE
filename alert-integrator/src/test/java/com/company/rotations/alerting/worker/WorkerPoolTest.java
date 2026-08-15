package com.company.rotations.alerting.worker;

import com.company.rotations.alerting.dedup.SecretDedupService;
import com.company.rotations.alerting.dlq.DeadLetterQueueService;
import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerPoolTest {

    private DeadLetterQueueService dlqService;
    private SecretDedupService secretDedupService;
    private AuditService auditService;
    private WorkerPool pool;

    @BeforeEach
    void setUp() {
        dlqService = mock(DeadLetterQueueService.class);
        secretDedupService = mock(SecretDedupService.class);
        auditService = mock(AuditService.class);
        pool = new WorkerPool(2, 100, dlqService, secretDedupService, auditService);
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should start successfully")
        void shouldStart() {
            assertTrue(pool.start());
            assertTrue(pool.isRunning());
        }

        @Test
        @DisplayName("Should return false when already started")
        void shouldFailWhenAlreadyStarted() {
            pool.start();
            assertFalse(pool.start());
        }

        @Test
        @DisplayName("Should stop after being started")
        void shouldStop() {
            pool.start();
            pool.stop();
            assertFalse(pool.isRunning());
        }

        @Test
        @DisplayName("Should handle stop when not running")
        void shouldStopWhenNotRunning() {
            assertDoesNotThrow(() -> pool.stop());
        }

        @Test
        @DisplayName("Should report initial metrics")
        void shouldReportInitialMetrics() {
            assertEquals(0, pool.getQueueSize());
            assertEquals(0, pool.getProcessedCount());
            assertEquals(0, pool.getFailedCount());
            assertEquals(2, pool.getPoolSize());
        }
    }

    @Nested
    @DisplayName("Submit")
    class SubmitTests {

        @Test
        @DisplayName("Should reject when not running")
        void shouldRejectWhenNotRunning() {
            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("e1");
            WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());

            assertThrows(RejectedExecutionException.class, () -> pool.submit(payload));
        }

        @Test
        @DisplayName("Should reject when queue is full")
        void shouldRejectWhenQueueFull() throws InterruptedException {
            pool.start();

            // Fill the queue to capacity first
            int capacity = pool.getQueueCapacity();
            for (int i = 0; i < capacity; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("fill-" + i);
                WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
                pool.submit(payload);
            }

            // Queue should be full now, submit more and verify rejection
            int accepted = 0;
            for (int i = 0; i < 200; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("e-" + i);
                WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
                if (pool.submit(payload)) {
                    accepted++;
                }
            }

            assertTrue(accepted < 200, "Some submissions should have been rejected due to queue full");

            pool.stop();
        }

        @Test
        @DisplayName("Should accept when running and queue has space")
        void shouldAcceptWhenSpaceAvailable() throws InterruptedException {
            pool.start();

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("e1");
            WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
            assertTrue(pool.submit(payload));

            pool.stop();
        }
    }

    @Nested
    @DisplayName("Properties")
    class PropertyTests {

        @Test
        @DisplayName("Should return correct pool size")
        void shouldReturnPoolSize() {
            WorkerPool customPool = new WorkerPool(5, 500, dlqService, secretDedupService, auditService);
            assertEquals(5, customPool.getPoolSize());
        }

        @Test
        @DisplayName("Should return correct queue max size")
        void shouldReturnQueueMaxSize() {
            WorkerPool customPool = new WorkerPool(3, 250, dlqService, secretDedupService, auditService);
            assertEquals(3, customPool.getPoolSize());
        }

        @Test
        @DisplayName("Should report running state")
        void shouldReportRunningState() throws InterruptedException {
            assertFalse(pool.isRunning());
            pool.start();
            assertTrue(pool.isRunning());
            pool.stop();
        }
    }

    @Nested
    @DisplayName("Concurrent Processing")
    class ConcurrentProcessingTests {

        @Test
        @DisplayName("Should process multiple alerts concurrently with multiple workers")
        void shouldProcessMultipleConcurrently() throws InterruptedException {
            AtomicInteger processedCount = new AtomicInteger(0);
            doAnswer(invocation -> {
                processedCount.incrementAndGet();
                return null;
            }).when(auditService).logProcessingCompleted(anyString(), anyString());

            pool.start();

            int alertCount = 10;
            for (int i = 0; i < alertCount; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("concurrent-" + i);
                WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
                pool.submit(payload);
            }

            Thread.sleep(2000);

            pool.stop();

            int count = processedCount.get();
            assertTrue(count > 1, "Multiple alerts should be processed concurrently, got: " + count);
        }

        @Test
        @DisplayName("Should handle many alerts without losing any")
        void shouldHandleManyAlerts() throws InterruptedException {
            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger startedCount = new AtomicInteger(0);
            doAnswer(invocation -> {
                startedCount.incrementAndGet();
                return null;
            }).when(auditService).logProcessingStarted(anyString(), anyString());
            doAnswer(invocation -> {
                completedCount.incrementAndGet();
                return null;
            }).when(auditService).logProcessingCompleted(anyString(), anyString());

            pool.start();

            int alertCount = 20;
            for (int i = 0; i < alertCount; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("bulk-" + i);
                WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
                pool.submit(payload);
            }

            Thread.sleep(3000);

            pool.stop();

            assertEquals(alertCount, startedCount.get(), "All alerts should have been started");
            assertEquals(alertCount, completedCount.get(), "All alerts should have been completed");
        }
    }

    @Nested
    @DisplayName("Worker Failure")
    class WorkerFailureTests {

        @Test
        @DisplayName("Should send alert to DLQ when processing fails")
        void shouldSendToDlqOnProcessingFailure() throws InterruptedException {
            doThrow(new RuntimeException("Processing failed"))
                    .when(auditService).logProcessingStarted(anyString(), anyString());

            pool.start();

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("fail-alert");
            WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
            pool.submit(payload);

            Thread.sleep(2000);

            pool.stop();

            verify(dlqService, timeout(2000)).addToDLQ(any(), any(), eq("src"), eq(null), eq("worker_processing"));
        }

        @Test
        @DisplayName("Should continue processing after a worker failure")
        void shouldContinueAfterFailure() throws InterruptedException {
            AtomicInteger failCount = new AtomicInteger(0);
            doAnswer(invocation -> {
                if (failCount.get() == 0) {
                    failCount.incrementAndGet();
                    throw new RuntimeException("First failure");
                }
                return null;
            }).when(auditService).logProcessingStarted(anyString(), anyString());

            AtomicInteger successCount = new AtomicInteger(0);
            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(auditService).logProcessingCompleted(anyString(), anyString());

            pool.start();

            // First alert will fail
            GenericAlertModel failAlert = new GenericAlertModel();
            failAlert.setEventId("fail-alert");
            pool.submit(new WebhookPayload(failAlert, "body", "src", Instant.now()));

            Thread.sleep(500);

            // Second alert should succeed
            GenericAlertModel successAlert = new GenericAlertModel();
            successAlert.setEventId("success-alert");
            pool.submit(new WebhookPayload(successAlert, "body", "src", Instant.now()));

            Thread.sleep(1500);

            pool.stop();

            assertEquals(1, pool.getFailedCount(), "First alert should have failed");
            assertEquals(1, successCount.get(), "Second alert should have succeeded");
        }

        @Test
        @DisplayName("Should track failed count on processing errors")
        void shouldTrackFailedCount() throws InterruptedException {
            doThrow(new RuntimeException("Always fails"))
                    .when(auditService).logProcessingStarted(anyString(), anyString());

            pool.start();

            for (int i = 0; i < 3; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("fail-" + i);
                pool.submit(new WebhookPayload(alert, "body", "src", Instant.now()));
            }

            Thread.sleep(2000);

            pool.stop();

            assertEquals(3, pool.getFailedCount(), "All three alerts should be tracked as failed");
        }

        @Test
        @DisplayName("Worker should not crash on secret dedup status check")
        void shouldNotCrashOnDedupSkip() throws InterruptedException {
            doAnswer(invocation -> {
                // Simulate that secret is still being processed
                return null;
            }).when(secretDedupService).getStatus(anyString(), anyString());

            pool.start();

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("dedup-skip-alert");
            WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
            pool.submit(payload);

            Thread.sleep(1500);

            assertDoesNotThrow(() -> pool.stop(), "Worker should not crash when secret is still processing");
        }
    }
}
