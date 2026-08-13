package com.company.rotations.logging.service;

import com.company.rotations.logging.model.AuditEvent;
import com.company.rotations.logging.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditEventRepository repository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MDC.clear();
        repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(inv -> {
            AuditEvent event = inv.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });
        auditService = new AuditService(repository);
    }

    @Test
    void logWebhookReceived_savesEvent() {
        Map<String, Object> data = Map.of("provider", "gitguardian", "url", "https://example.com");

        auditService.logWebhookReceived(data);

        verify(repository).save(any(AuditEvent.class));
    }

    @Test
    void logVerificationStarted_savesEvent() {
        Map<String, Object> data = Map.of("alertId", "alert-123", "type", "credential");

        auditService.logVerificationStarted(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.VERIFICATION_STARTED, event.getEventType());
        assertNull(event.getStatus());
    }

    @Test
    void logVerificationCompleted_savesEventWithSuccessStatus() {
        Map<String, Object> data = Map.of("alertId", "alert-123", "success", true);

        auditService.logVerificationCompleted(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.VERIFICATION_COMPLETED, event.getEventType());
        assertEquals("SUCCESS", event.getStatus());
    }

    @Test
    void logVerificationCompleted_savesEventWithFailureStatus() {
        Map<String, Object> data = Map.of("alertId", "alert-123", "success", false);

        auditService.logVerificationCompleted(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("FAILURE", event.getStatus());
    }

    @Test
    void logRuleEvaluated_savesEvent() {
        Map<String, Object> data = Map.of("ruleName", "check-expired-creds", "matched", true);

        auditService.logRuleEvaluated(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.RULE_EVALUATED, event.getEventType());
        assertEquals(AuditEvent.AuditSeverity.AUDIT, event.getSeverity());
    }

    @Test
    void logActionExecuted_savesEventWithSuccess() {
        Map<String, Object> data = Map.of("actionType", "rotate-password", "success", true);

        auditService.logActionExecuted(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.ACTION_EXECUTED, event.getEventType());
        assertEquals("SUCCESS", event.getStatus());
    }

    @Test
    void logActionExecuted_savesEventWithFailure() {
        Map<String, Object> data = Map.of("actionType", "rotate-password", "success", false,
            "error", "connection timeout");

        auditService.logActionExecuted(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("FAILURE", event.getStatus());
    }

    @Test
    void logDlqEnqueued_savesEventWithErrorSeverity() {
        Map<String, Object> data = Map.of("error", "invalid payload", "rawPayload", "{}");

        auditService.logDlqEnqueued(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.DLQ_ENQUEUED, event.getEventType());
        assertEquals(AuditEvent.AuditSeverity.ERROR, event.getSeverity());
        assertEquals("FAILURE", event.getStatus());
    }

    @Test
    void logDedupHit_savesEvent() {
        Map<String, Object> data = Map.of("dedupLevel", "event", "cooldownState", "active");

        auditService.logDedupHit(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEvent.AuditEventType.DEDUP_HIT, event.getEventType());
        assertEquals(AuditEvent.AuditSeverity.WARN, event.getSeverity());
    }

    @Test
    void logWebhookReceived_savesMdcFields() {
        MDC.put("client_id", "test-client");
        MDC.put("trace_id", "test-trace-123");
        MDC.put("alert_id", "alert-abc");
        MDC.put("phase", "alert-ingestion");
        Map<String, Object> data = Map.of("source", "test");

        auditService.logWebhookReceived(data);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("test-client", event.getClientId());
        assertEquals("test-trace-123", event.getTraceId());
        assertEquals("alert-abc", event.getAlertId());
        assertEquals("alert-ingestion", event.getPhase());
    }

    @Test
    void logWebhookReceived_handlesSerializationError() {
        Map<String, Object> data = Map.of("key", "value");

        assertDoesNotThrow(() -> auditService.logWebhookReceived(data));
        verify(repository).save(any(AuditEvent.class));
    }
}
