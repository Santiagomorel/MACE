package com.company.rotations.actionexecutor.integration;

import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import com.company.rotations.actionexecutor.service.AuditTrailService;
import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.actionexecutor.strategy.impl.SlackNotificationService;
import com.company.rotations.actionexecutor.strategy.impl.EmailNotificationService;
import com.company.rotations.actionexecutor.strategy.impl.TicketNotificationService;
import com.company.rotations.actionexecutor.strategy.impl.AwsSnsNotificationService;
import com.company.rotations.actionexecutor.strategy.NotificationDispatcherStrategy;
import com.company.rotations.models.Severidad;
import com.company.rotations.models.Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FullFlowIntegrationTest {

    private final UUID testAlertId = UUID.randomUUID();
    private final String tenantId = "tenant-123";
    private final String credentialId = "AKIAEXAMPLE1234567890";

    @Mock
    private AuditTrailService auditTrailService;

    @Test
    void testFullRotationFlowPENDINGtoSUCCESS() {
        // Simulate full rotation flow: PENDING -> ROTATING -> SUCCESS
        RotationStateMachine sm = new RotationStateMachine("e2e-test", testAlertId);

        assertEquals(RotationState.PENDING, sm.getCurrentState());

        RotationTransitionDto t1 = sm.transitionTo(RotationState.ROTATING, "Rotation started");
        assertEquals(RotationState.ROTATING, sm.getCurrentState());
        assertNotNull(t1.getTimestamp());
        assertEquals(testAlertId, t1.getAlertId());

        RotationTransitionDto t2 = sm.transitionTo(RotationState.SUCCESS, "Rotation completed");
        assertEquals(RotationState.SUCCESS, sm.getCurrentState());
        assertEquals("ROTATING", t2.getFromState());
        assertEquals("SUCCESS", t2.getToState());

        // Verify audit trail data
        var log = sm.getTransitionLog();
        assertEquals(2, log.size());
        assertTrue(log.get(0).getDurationMs() >= 0);
        assertTrue(log.get(1).getDurationMs() >= 0);

        assertFalse(sm.isTerminalState());
    }

    @Test
    void testFullRotationFlowWithRetries() {
        // Simulate flow: PENDING -> ROTATING -> FAIL -> ROTATING(retry) -> FAIL -> ESCALATE
        RotationStateMachine sm = new RotationStateMachine("e2e-retry", testAlertId);

        sm.transitionTo(RotationState.ROTATING, "Attempt 1");
        sm.transitionTo(RotationState.FAIL, "Attempt 1 failed");
        sm.incrementAttempt();

        sm.transitionTo(RotationState.ROTATING, "Attempt 2");
        sm.transitionTo(RotationState.FAIL, "Attempt 2 failed");
        sm.incrementAttempt();

        sm.transitionTo(RotationState.ROTATING, "Attempt 3");
        sm.transitionTo(RotationState.FAIL, "Attempt 3 failed");
        sm.incrementAttempt();

        sm.transitionTo(RotationState.ESCALATE, "All retries exhausted");

        assertEquals(RotationState.ESCALATE, sm.getCurrentState());
        assertTrue(sm.isTerminalState());
        assertEquals(10, sm.getTransitionLog().size());
    }

    @Test
    void testFullRotationWithAuditData() {
        RotationStateMachine sm = new RotationStateMachine("e2e-audit", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Started", null);
        sm.transitionTo(RotationState.FAIL, "Failed", "AWS error");

        var log = sm.getTransitionLog();
        RotationTransitionDto failTransition = log.get(1);

        assertEquals("Failed", failTransition.getReason());
        assertEquals("AWS error", failTransition.getErrorMessage());
        assertEquals(RotationState.ROTATING.name(), failTransition.getFromState());
        assertEquals(RotationState.FAIL.name(), failTransition.getToState());
    }

    @Test
    void testNotificationResultStructure() {
        NotificationStrategy.NotificationResult result =
                new NotificationStrategy.NotificationResult(true, "slack", "sent");

        assertTrue(result.isSuccess());
        assertEquals("slack", result.getChannel());
        assertEquals("sent", result.getMessage());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testNotificationResultWithFailure() {
        NotificationStrategy.NotificationResult result =
                new NotificationStrategy.NotificationResult(
                        false, "email", "failed", "Connection timeout"
                );

        assertFalse(result.isSuccess());
        assertEquals("email", result.getChannel());
        assertEquals("Connection timeout", result.getErrorMessage());
    }

    @Test
    void testSeverityLevels() {
        Severidad[] severities = Severidad.values();
        assertEquals(4, severities.length);

        assertTrue(Severidad.CRITICO.getRank() > Severidad.ALTO.getRank());
        assertTrue(Severidad.ALTO.getRank() > Severidad.MEDIA.getRank());
        assertTrue(Severidad.MEDIA.getRank() > Severidad.BAJO.getRank());
    }

    @Test
    void testRotationResultFullFlow() {
        RotationResult result = new RotationResult();
        result.setAlertId(testAlertId);
        result.setSuccess(true);
        result.setMessage("Rotation completed");
        result.setAttempts(1);
        result.setNewKeyId("AKIANEW12345");
        result.setStartTime(Instant.now().minusSeconds(30));
        result.setEndTime(Instant.now());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getAttempts());
        assertEquals("AKIANEW12345", result.getNewKeyId());
        assertEquals(testAlertId, result.getAlertId());
        assertTrue(result.getDurationMs() > 0);
    }

    @Test
    void testRotationResultMultipleAttempts() {
        RotationResult result = new RotationResult();
        result.setAlertId(testAlertId);
        result.setSuccess(false);
        result.setAttempts(3);
        result.setErrorMessage("All 3 attempts failed");
        result.setStartTime(Instant.now().minusSeconds(120));
        result.setEndTime(Instant.now());

        assertFalse(result.isSuccess());
        assertEquals(3, result.getAttempts());
        assertTrue(result.getDurationMs() > 0);
    }

    @Test
    void testStateMachinewithTimeoutAndEscalation() {
        RotationStateMachine sm = new RotationStateMachine("e2e-timeout", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Starting");

        // Timeout from any state
        RotationTransitionDto timeout = sm.timeoutTransition();
        assertEquals(RotationState.TIMEOUT, sm.getCurrentState());
        assertTrue(sm.isTerminalState());
        assertEquals("Global timeout of 5 minutes exceeded", timeout.getReason());
    }

    @Test
    void testNotificationDispatcherIntegration() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                tenantId, testAlertId, Severidad.CRITICO, credentialId,
                List.of("slack", "email")
        );

        assertEquals(2, results.size());
        assertEquals("slack", results.get(0).getChannel());
        assertEquals("email", results.get(1).getChannel());
    }

    @Test
    void testAuditTrailDataStructure() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "PENDING", "ROTATING",
                Instant.now(), 100L, "Test transition",
                1, null
        );

        assertEquals(testAlertId, dto.getAlertId());
        assertEquals("PENDING", dto.getFromState());
        assertEquals("ROTATING", dto.getToState());
        assertEquals(100L, dto.getDurationMs());
        assertEquals("Test transition", dto.getReason());
        assertEquals(Integer.valueOf(1), dto.getAttemptNumber());
        assertNull(dto.getErrorMessage());
    }
}
