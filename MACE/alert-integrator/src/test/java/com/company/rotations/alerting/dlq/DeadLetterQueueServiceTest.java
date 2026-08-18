package com.company.rotations.alerting.dlq;

import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeadLetterQueueServiceTest {

    private AlertDLQRepository dlqRepository;
    private AuditService auditService;
    private DeadLetterQueueService service;

    @BeforeEach
    void setUp() {
        dlqRepository = mock(AlertDLQRepository.class);
        auditService = mock(AuditService.class);
        service = new DeadLetterQueueService(dlqRepository, auditService, 3, 7);
    }

    @Nested
    @DisplayName("Process Alert")
    class ProcessAlertTests {

        @Test
        @DisplayName("Should log processing started without error")
        void shouldLogProcessingStarted() {
            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("event-123");
            WebhookPayload payload = new WebhookPayload(alert, "raw-body", "gitguardian", Instant.now());

            assertDoesNotThrow(() -> service.processAlert(payload));
            verify(auditService).logProcessingStarted("gitguardian", "event-123");
        }

        @Test
        @DisplayName("Should handle null alert gracefully")
        void shouldHandleNullAlert() {
            // Even with null alert, should not throw
            WebhookPayload payload = new WebhookPayload(null, "raw-body", "gitguardian", Instant.now());
            assertDoesNotThrow(() -> service.processAlert(payload));
        }
    }

    @Nested
    @DisplayName("Add to DLQ")
    class AddToDLQTests {

        @Test
        @DisplayName("Should save new DLQ entry")
        void shouldSaveNewEntry() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("value_hash", "hash123");

            Exception ex = new RuntimeException("Test error");
            service.addToDLQ(ex, payload, "gitguardian", "evt-1", "pipeline_error");

            verify(dlqRepository).save(any(AlertDLQEntry.class));
        }

        @Test
        @DisplayName("Should handle null payload")
        void shouldHandleNullPayload() {
            Exception ex = new RuntimeException("Test error");
            assertDoesNotThrow(() -> service.addToDLQ(ex, null, "src", "evt", "type"));
            verify(dlqRepository).save(any(AlertDLQEntry.class));
        }

        @Test
        @DisplayName("Should increment retry for existing entry")
        void shouldIncrementRetryForExisting() {
            AlertDLQEntry existing = new AlertDLQEntry("payload", "old error", "src", "evt-1", "type");
            when(dlqRepository.findBySourceAndSourceEventId("src", "evt-1"))
                    .thenReturn(Optional.of(existing));

            Map<String, Object> payload = new HashMap<>();
            payload.put("value_hash", "hash123");

            service.addToDLQ(new RuntimeException("New error"), payload, "src", "evt-1", "type");

            verify(dlqRepository).save(existing);
            assertEquals(1, existing.getRetryCount());
            assertEquals("New error", existing.getErrorMessage());
        }

        @Test
        @DisplayName("Should archive entry after max retries")
        void shouldArchiveAfterMaxRetries() {
            AlertDLQEntry existing = new AlertDLQEntry("payload", "old error", "src", "evt-1", "type");
            existing.setRetryCount(3); // already at max
            when(dlqRepository.findBySourceAndSourceEventId("src", "evt-1"))
                    .thenReturn(Optional.of(existing));

            Map<String, Object> payload = new HashMap<>();
            payload.put("value_hash", "hash123");

            service.addToDLQ(new RuntimeException("Error"), payload, "src", "evt-1", "type");

            assertEquals(AlertDLQEntry.DLQStatus.ARCHIVED, existing.getStatus());
        }

        @Test
        @DisplayName("Should handle repository save failure")
        void shouldHandleSaveFailure() {
            when(dlqRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            Map<String, Object> payload = new HashMap<>();
            payload.put("value_hash", "hash123");

            // Should not throw
            assertDoesNotThrow(() ->
                    service.addToDLQ(new RuntimeException("Test error"), payload, "src", "evt-1", "type"));
        }

        @Test
        @DisplayName("Should treat null valueHash as new entry")
        void shouldTreatNullHashAsNew() {
            when(dlqRepository.findBySourceAndSourceEventId(any(), any()))
                    .thenReturn(Optional.empty());

            Map<String, Object> payload = new HashMap<>();
            // no value_hash

            service.addToDLQ(new RuntimeException("Error"), payload, "src", "evt-1", "type");
            verify(dlqRepository).save(any());
        }
    }

    @Nested
    @DisplayName("Max Retries Config")
    class MaxRetriesTests {

        @Test
        @DisplayName("Should use configured max retries")
        void shouldUseConfiguredMaxRetries() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            DeadLetterQueueService customService = new DeadLetterQueueService(dlqRepository, auditService, 2, 7);

            when(dlqRepository.findBySourceAndSourceEventId(any(), any()))
                    .thenReturn(Optional.of(entry));

            Map<String, Object> payload = new HashMap<>();
            payload.put("value_hash", "h1");
            customService.addToDLQ(new RuntimeException("err"), payload, "s", "evt", "t");
            // retryCount = 1, max = 2, not archived

            customService.addToDLQ(new RuntimeException("err"), payload, "s", "evt", "t");
            // retryCount = 2, max = 2, should be archived
            assertEquals(AlertDLQEntry.DLQStatus.ARCHIVED, entry.getStatus());
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("Should delete old entries")
        void shouldDeleteOldEntries() {
            when(dlqRepository.deleteOlderThan(any())).thenReturn(5);

            service.cleanupExpiredEntries();

            verify(dlqRepository).deleteOlderThan(any());
        }
    }
}
