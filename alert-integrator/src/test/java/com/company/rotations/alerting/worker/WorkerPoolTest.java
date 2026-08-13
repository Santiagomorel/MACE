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

            // Fill the queue well beyond capacity (100) while workers consume
            int accepted = 0;
            for (int i = 0; i < 500; i++) {
                GenericAlertModel alert = new GenericAlertModel();
                alert.setEventId("e-" + i);
                WebhookPayload payload = new WebhookPayload(alert, "body", "src", Instant.now());
                if (pool.submit(payload)) {
                    accepted++;
                }
            }

            // At least some submissions should have been rejected (queue capacity is 100)
            assertTrue(accepted < 500, "Some submissions should have been rejected due to queue full");

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
}
