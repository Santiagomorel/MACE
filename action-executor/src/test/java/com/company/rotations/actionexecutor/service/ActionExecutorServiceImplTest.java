package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.strategy.NotificationDispatcherStrategy;
import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.logging.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionExecutorServiceImplTest {

    @Mock
    private AwsRotationService awsRotationService;

    @Mock
    private NotificationDispatcherStrategy notificationDispatcher;

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private AuditService auditService;

    private ActionExecutorServiceImpl executor;

    private final UUID testAlertId = UUID.randomUUID();
    private final com.company.rotations.models.Credential testCredential = createCredential();

    @BeforeEach
    void setUp() {
        executor = new ActionExecutorServiceImpl(
                awsRotationService, notificationDispatcher,
                auditTrailService, auditService
        );
    }

    @Test
    void executeRotation_success_flow() {
        RotationResult rotationResult = new RotationResult(testAlertId, true, "Rotation successful");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ActionExecutor.ActionExecutionResult result = executor.executeRotation(
                testCredential, "tenant1", com.company.rotations.models.Severidad.CRITICO, testAlertId
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getRotationResult());
        assertTrue(result.getRotationResult().isSuccess());

        verify(auditService, times(2)).logActionExecuted(anyMap());
    }

    @Test
    void executeRotation_failure_flow() {
        RotationResult rotationResult = new RotationResult(testAlertId, false, "Rotation failed");
        rotationResult.setErrorMessage("Rotation failed");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);

        ActionExecutor.ActionExecutionResult result = executor.executeRotation(
                testCredential, "tenant1", com.company.rotations.models.Severidad.ALTO, testAlertId
        );

        assertFalse(result.isSuccess());
        assertTrue(result.isEscalated());
        assertEquals("Rotation failed", result.getErrorMessage());

        verify(auditTrailService).logEscalation(eq("tenant1"), eq(testAlertId), eq(com.company.rotations.models.Severidad.ALTO), anyString(), eq(3));
        verify(auditService, times(2)).logActionExecuted(anyMap());
    }

    @Test
    void executeRotation_auditLogInitialCalled() {
        RotationResult rotationResult = new RotationResult(testAlertId, true, "OK");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        executor.executeRotation(testCredential, "tenant1", com.company.rotations.models.Severidad.MEDIA, testAlertId);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService, atLeast(1)).logActionExecuted(captor.capture());

        Map<String, Object> firstCall = captor.getValue();
        assertEquals(testAlertId.toString(), firstCall.get("alert_id"));
        assertEquals("tenant1", firstCall.get("tenant_id"));
        assertEquals("MEDIA", firstCall.get("severity"));
        assertEquals("AKIA1234", firstCall.get("credential_id"));
    }

    @Test
    void executeRotation_auditLogFinalCalled() {
        RotationResult rotationResult = new RotationResult(testAlertId, true, "OK");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        executor.executeRotation(testCredential, "tenant1", com.company.rotations.models.Severidad.ALTO, testAlertId);

        verify(auditService, times(2)).logActionExecuted(anyMap());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService, times(2)).logActionExecuted(captor.capture());

        List<Map<String, Object>> allValues = captor.getAllValues();
        assertEquals(2, allValues.size());

        Map<String, Object> secondCall = allValues.get(1);
        assertEquals(testAlertId.toString(), secondCall.get("alert_id"));
        assertEquals(true, secondCall.get("success"));
        assertEquals(false, secondCall.get("escalated"));
    }

    @Test
    void executeRotation_auditFailureLogged() {
        RotationResult rotationResult = new RotationResult(testAlertId, true, "OK");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("Audit service down")).when(auditService).logActionExecuted(anyMap());

        assertDoesNotThrow(() ->
                executor.executeRotation(testCredential, "tenant1", com.company.rotations.models.Severidad.BAJO, testAlertId)
        );

        verify(auditService, times(2)).logActionExecuted(anyMap());
    }

    @Test
    void executeRotation_escalation_logged() {
        RotationResult rotationResult = new RotationResult(testAlertId, false, "All rotations failed");
        rotationResult.setErrorMessage("All rotations failed");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);

        executor.executeRotation(testCredential, "tenant2", com.company.rotations.models.Severidad.CRITICO, testAlertId);

        verify(auditTrailService).logEscalation(eq("tenant2"), eq(testAlertId), eq(com.company.rotations.models.Severidad.CRITICO),
                eq("All rotations failed"), eq(3));
    }

    @Test
    void executeRotation_credentialTypeUnknown() {
        com.company.rotations.models.Credential credNoType = new com.company.rotations.models.Credential();
        credNoType.setId(UUID.randomUUID());
        credNoType.setKeyId("AKIA0000");
        credNoType.setCredentialType(null);

        RotationResult rotationResult = new RotationResult(credNoType.getId(), true, "OK");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        executor.executeRotation(credNoType, "tenant1", com.company.rotations.models.Severidad.MEDIA, credNoType.getId());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService, atLeast(1)).logActionExecuted(captor.capture());

        Map<String, Object> firstCall = captor.getAllValues().get(0);
        assertEquals("unknown", firstCall.get("credential_type"));
    }

    @Test
    void sendNotifications_delegatesToDispatcher() {
        when(notificationDispatcher.dispatchNotifications(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<NotificationStrategy.NotificationResult> results = executor.sendNotifications(
                "tenant1", testAlertId, com.company.rotations.models.Severidad.ALTO, "AKIA1234", List.of("slack", "email")
        );

        assertNotNull(results);
        verify(notificationDispatcher).dispatchNotifications(
                eq("tenant1"), eq(testAlertId), eq(com.company.rotations.models.Severidad.ALTO), eq("AKIA1234"),
                eq(List.of("slack", "email"))
        );
    }

    @Test
    void sendNotifications_returnsDispatcherResults() {
        NotificationStrategy.NotificationResult mockResult = new NotificationStrategy.NotificationResult(
                true, "slack", "Sent successfully"
        );
        when(notificationDispatcher.dispatchNotifications(any(), any(), any(), any(), any()))
                .thenReturn(List.of(mockResult));

        List<NotificationStrategy.NotificationResult> results = executor.sendNotifications(
                "tenant1", testAlertId, com.company.rotations.models.Severidad.BAJO, "AKIA1234", List.of("slack")
        );

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("slack", results.get(0).getChannel());
    }

    @Test
    void executeRotation_success_sendsNotifications() {
        RotationResult rotationResult = new RotationResult(testAlertId, true, "OK");
        when(awsRotationService.executeRotation(any(), any(), any(), any())).thenReturn(rotationResult);
        when(notificationDispatcher.dispatchNotificationsAsync(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        executor.executeRotation(testCredential, "tenant1", com.company.rotations.models.Severidad.CRITICO, testAlertId);

        verify(notificationDispatcher).dispatchNotificationsAsync(
                eq("tenant1"), eq(testAlertId), eq(com.company.rotations.models.Severidad.CRITICO), eq("AKIA1234"),
                eq(List.of("slack", "email"))
        );
    }

    private com.company.rotations.models.Credential createCredential() {
        com.company.rotations.models.Credential credential =
                new com.company.rotations.models.Credential(
                        UUID.randomUUID(), "tenant1",
                        com.company.rotations.models.Credential.CredentialType.ACCESS_KEY,
                        "arn:aws:iam::123456789:user/rotator",
                        com.company.rotations.models.Credential.CredentialStatus.ACTIVE,
                        "AKIA1234", 86400L,
                        com.company.rotations.models.Credential.CredentialPrefix.AKIA
                );
        return credential;
    }
}
