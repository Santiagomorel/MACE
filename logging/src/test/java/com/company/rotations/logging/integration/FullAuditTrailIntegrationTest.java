package com.company.rotations.logging.integration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.rotations.logging.model.AuditEvent;
import com.company.rotations.logging.repository.AuditEventRepository;
import com.company.rotations.logging.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Integration Tests - Full Audit Trail with MDC and Logging")
class FullAuditTrailIntegrationTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    private AuditService auditService;
    private ListAppender<ILoggingEvent> listAppender;
    private ch.qos.logback.classic.Logger auditLogger;
    private ch.qos.logback.classic.Logger infoLogger;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEventRepository);
        listAppender = new ListAppender<>();
        listAppender.start();

        auditLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("AUDIT");
        auditLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        auditLogger.addAppender(listAppender);

        infoLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.company.rotations.logging");
        infoLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    @Test
    @DisplayName("Complete audit trail: webhook -> verify -> decision -> action")
    void testFullPipelineAuditTrail() {
        String traceId = "trace-pipeline-001";
        String alertId = "alert-full-pipeline-001";
        String clientId = "test-client-001";

        org.slf4j.MDC.put("trace_id", traceId);
        org.slf4j.MDC.put("alert_id", alertId);
        org.slf4j.MDC.put("client_id", clientId);

        // Phase 1: Webhook received
        org.slf4j.MDC.put("phase", "webhook");
        auditService.logWebhookReceived(Map.of(
                "source", "gitguardian",
                "url", "https://api.gitguardian.com/v1/scan",
                "provider", "gitlab"
        ));

        // Phase 2: Verification started and completed
        org.slf4j.MDC.put("phase", "verification");
        auditService.logVerificationStarted(Map.of(
                "event_id", alertId,
                "credential_id", "AKIAIOSFODNN7EXAMPLE",
                "provider", "aws"
        ));

        auditService.logVerificationCompleted(Map.of(
                "event_id", alertId,
                "account_id", "client-account-456",
                "status", "VERIFIED",
                "success", true,
                "action_matrix_size", 5
        ));

        // Phase 3: Rule evaluated (decision)
        org.slf4j.MDC.put("phase", "decision");
        auditService.logRuleEvaluated(Map.of(
                "ruleName", "check-expired-creds",
                "matched", true,
                "severity", "high",
                "recommendation", "rotate-credentials"
        ));

        // Phase 4: Action executed
        org.slf4j.MDC.put("phase", "action");
        auditService.logActionExecuted(Map.of(
                "actionType", "rotate-aws-key",
                "success", true,
                "rotationDurationMs", 4500,
                "newCredentialStored", true
        ));

        // Verify repository was called 5 times (one per audit event)
        verify(auditEventRepository, times(5)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();
        assertEquals(5, events.size());

        // Verify each phase has correct event types
        assertEquals(AuditEvent.AuditEventType.WEBHOOK_RECEIVED, events.get(0).getEventType());
        assertEquals("webhook", events.get(0).getPhase());
        assertEquals(AuditEvent.AuditEventType.VERIFICATION_STARTED, events.get(1).getEventType());
        assertEquals("verification", events.get(1).getPhase());
        assertEquals(AuditEvent.AuditEventType.VERIFICATION_COMPLETED, events.get(2).getEventType());
        assertEquals("verification", events.get(2).getPhase());
        assertEquals(AuditEvent.AuditEventType.RULE_EVALUATED, events.get(3).getEventType());
        assertEquals("decision", events.get(3).getPhase());
        assertEquals(AuditEvent.AuditEventType.ACTION_EXECUTED, events.get(4).getEventType());
        assertEquals("action", events.get(4).getPhase());

        // Verify MDC fields are captured correctly
        events.forEach(event -> {
            assertEquals(traceId, event.getTraceId());
            assertEquals(alertId, event.getAlertId());
            assertEquals(clientId, event.getClientId());
            assertNotNull(event.getCreatedAt());
        });

        // Verify AUDIT logger received 5 events
        List<ILoggingEvent> auditEvents = listAppender.list;
        assertEquals(5, auditEvents.size());
        auditEvents.forEach(event -> {
            assertNotNull(event.getMessage());
            assertTrue(event.getMessage().startsWith("Audit event:"));
        });
    }

    @Test
    @DisplayName("Audit trail preserves distinct phases for same alert")
    void testDistinctPhasesForSameAlert() {
        String alertId = "alert-multi-phase";
        String traceId = "trace-multi-phase-001";

        org.slf4j.MDC.put("trace_id", traceId);
        org.slf4j.MDC.put("alert_id", alertId);

        org.slf4j.MDC.put("phase", "webhook");
        auditService.logWebhookReceived(Map.of("source", "github", "repo", "myapp"));

        org.slf4j.MDC.put("phase", "verification");
        auditService.logVerificationStarted(Map.of("credential_id", "cred-1"));

        org.slf4j.MDC.put("phase", "decision");
        auditService.logRuleEvaluated(Map.of("rule", "test-rule", "matched", false));

        org.slf4j.MDC.put("phase", "action");
        auditService.logActionExecuted(Map.of("actionType", "notify", "success", true));

        verify(auditEventRepository, times(4)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();
        assertEquals(4, events.size());

        java.util.Set<String> phases = events.stream()
                .map(AuditEvent::getPhase)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(4, phases.size(), "Should have 4 distinct phases");
        assertTrue(phases.contains("webhook"));
        assertTrue(phases.contains("verification"));
        assertTrue(phases.contains("decision"));
        assertTrue(phases.contains("action"));
    }

    @Test
    @DisplayName("Audit trail captures DLQ and dedup events with correct severity")
    void testDlqAndDedupEvents() {
        String alertId = "alert-dlq-dedup";

        org.slf4j.MDC.put("alert_id", alertId);
        org.slf4j.MDC.put("phase", "webhook");
        auditService.logWebhookReceived(Map.of("source", "test", "url", "https://example.com"));

        org.slf4j.MDC.put("phase", "dedup");
        auditService.logDedupHit(Map.of(
                "dedupLevel", "event",
                "reason", "same-event-within-cooldown",
                "cooldownState", "active"
        ));

        org.slf4j.MDC.put("phase", "dlq");
        auditService.logDlqEnqueued(Map.of(
                "error", "invalid payload format",
                "rawPayload", "malformed-json"
        ));

        verify(auditEventRepository, times(3)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();

        assertEquals(AuditEvent.AuditEventType.WEBHOOK_RECEIVED, events.get(0).getEventType());
        assertEquals(AuditEvent.AuditEventType.DEDUP_HIT, events.get(1).getEventType());
        assertEquals(AuditEvent.AuditEventType.DLQ_ENQUEUED, events.get(2).getEventType());

        assertEquals(AuditEvent.AuditSeverity.WARN, events.get(1).getSeverity());
        assertEquals(AuditEvent.AuditSeverity.ERROR, events.get(2).getSeverity());
        assertEquals("FAILURE", events.get(2).getStatus());
    }

    @Test
    @DisplayName("Verification failure results in FAILURE status")
    void testAuditTrailWithVerificationFailure() {
        String alertId = "alert-verify-fail";

        org.slf4j.MDC.put("alert_id", alertId);
        org.slf4j.MDC.put("phase", "verification");

        auditService.logVerificationStarted(Map.of(
                "event_id", alertId,
                "credential_id", "AKIAFAIL1234567890",
                "provider", "aws"
        ));

        auditService.logVerificationCompleted(Map.of(
                "event_id", alertId,
                "account_id", "unknown",
                "success", false,
                "reason", "credential-invalid"
        ));

        verify(auditEventRepository, times(2)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();

        AuditEvent completedEvent = events.stream()
                .filter(e -> e.getEventType() == AuditEvent.AuditEventType.VERIFICATION_COMPLETED)
                .findFirst()
                .orElseThrow();

        assertEquals("FAILURE", completedEvent.getStatus());
    }

    @Test
    @DisplayName("Action execution failure results in FAILURE status")
    void testAuditTrailWithActionFailure() {
        String alertId = "alert-action-fail";

        org.slf4j.MDC.put("alert_id", alertId);
        org.slf4j.MDC.put("phase", "decision");
        auditService.logRuleEvaluated(Map.of(
                "ruleName", "rotate-key",
                "matched", true
        ));

        org.slf4j.MDC.put("phase", "action");
        auditService.logActionExecuted(Map.of(
                "actionType", "rotate-aws-key",
                "success", false,
                "error", "AWS connection timeout"
        ));

        verify(auditEventRepository, times(2)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();

        AuditEvent actionEvent = events.stream()
                .filter(e -> e.getEventType() == AuditEvent.AuditEventType.ACTION_EXECUTED)
                .findFirst()
                .orElseThrow();

        assertEquals("FAILURE", actionEvent.getStatus());
    }

    @Test
    @DisplayName("Multiple alerts have independent audit trails")
    void testMultipleAlertsIndependentTrails() {
        String alertId1 = "alert-individual-001";
        String alertId2 = "alert-individual-002";

        org.slf4j.MDC.put("phase", "webhook");

        org.slf4j.MDC.put("alert_id", alertId1);
        auditService.logWebhookReceived(Map.of("source", "gitguardian"));

        org.slf4j.MDC.put("alert_id", alertId2);
        auditService.logWebhookReceived(Map.of("source", "github"));

        verify(auditEventRepository, times(2)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();
        assertEquals(2, events.size());
        assertEquals(alertId1, events.get(0).getAlertId());
        assertEquals(alertId2, events.get(1).getAlertId());
        assertNotEquals(events.get(0), events.get(1), "Events should be different objects");
    }

    @Test
    @DisplayName("Audit events are ordered by creation time within same alert")
    void testAuditEventsOrderedByCreationTime() {
        String alertId = "alert-ordered-001";

        org.slf4j.MDC.put("alert_id", alertId);

        org.slf4j.MDC.put("phase", "webhook");
        auditService.logWebhookReceived(Map.of("source", "test"));

        org.slf4j.MDC.put("phase", "verification");
        auditService.logVerificationStarted(Map.of("credential_id", "cred-1"));

        org.slf4j.MDC.put("phase", "decision");
        auditService.logRuleEvaluated(Map.of("rule", "test"));

        org.slf4j.MDC.put("phase", "action");
        auditService.logActionExecuted(Map.of("actionType", "rotate", "success", true));

        verify(auditEventRepository, times(4)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();

        for (int i = 0; i < events.size() - 1; i++) {
            assertTrue(events.get(i).getCreatedAt().isBefore(events.get(i + 1).getCreatedAt())
                    || events.get(i).getCreatedAt().isEqual(events.get(i + 1).getCreatedAt()));
        }
    }

    @Test
    @DisplayName("MDC fields from different alerts are not mixed")
    void testMdcFieldsIsolation() {
        String alertId1 = "alert-isolation-1";
        String alertId2 = "alert-isolation-2";
        String traceId1 = "trace-1";
        String traceId2 = "trace-2";

        org.slf4j.MDC.put("alert_id", alertId1);
        org.slf4j.MDC.put("trace_id", traceId1);
        auditService.logWebhookReceived(Map.of("source", "source1"));

        org.slf4j.MDC.clear();
        org.slf4j.MDC.put("alert_id", alertId2);
        org.slf4j.MDC.put("trace_id", traceId2);
        auditService.logWebhookReceived(Map.of("source", "source2"));

        verify(auditEventRepository, times(2)).save(auditEventCaptor.capture());

        List<AuditEvent> events = auditEventCaptor.getAllValues();
        assertEquals(alertId1, events.get(0).getAlertId());
        assertEquals(traceId1, events.get(0).getTraceId());
        assertEquals(alertId2, events.get(1).getAlertId());
        assertEquals(traceId2, events.get(1).getTraceId());
    }

    @Test
    @DisplayName("Null MDC fields are stored as null in audit event")
    void testNullMdcFields() {
        org.slf4j.MDC.clear();
        auditService.logWebhookReceived(Map.of("source", "test"));

        verify(auditEventRepository).save(auditEventCaptor.capture());
        AuditEvent event = auditEventCaptor.getValue();

        assertNull(event.getTraceId());
        assertNull(event.getAlertId());
        assertNull(event.getClientId());
    }
}
