package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;
import software.amazon.awssdk.services.sts.StsClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsRotationServiceTest {

    @Mock
    private StsClient stsClient;

    @Mock
    private IamClient iamClient;

    @Mock
    private VaultService vaultService;

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private com.company.rotations.logging.service.AuditService auditService;

    private AwsRotationService rotationService;

    private final UUID testAlertId = UUID.randomUUID();
    private final com.company.rotations.models.Credential testCredential = createCredential();
    private RotationStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new RotationStateMachine("test-rotation", testAlertId);
        rotationService = new AwsRotationService(
                stsClient, iamClient, vaultService, auditTrailService, auditService) {
            @Override
            protected void backoffForRetry(long backoffMs) {
                // no-op in tests
            }

            @Override
            protected void waitForIamPropagation(String keyId) {
                // no-op in tests
            }
        };
    }

    @Test
    void executeRotation_successFirstAttempt() {
        setupSuccessfulRotationMock();

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.CRITICO
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getAttempts());
        assertNotNull(result.getNewKeyId());
        assertEquals("AKIA_NEWKEY", result.getNewKeyId());
        assertTrue(result.getMessage().contains("AKIA_NEWKEY"));
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertNotNull(result.getDurationMs());
        assertTrue(result.getDurationMs() >= 0);

        verify(vaultService).storeNewCredentials(
                eq("tenant1"), eq("AKIA_NEWKEY"), anyString(), eq("AKIA_NEWKEY"), anyString()
        );
    }

    @Test
    void executeRotation_successAfterRetry() {
        doThrow(new RuntimeException("Connection timeout"))
                .doReturn(software.amazon.awssdk.services.iam.model.UpdateAccessKeyResponse.builder().build())
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        CreateAccessKeyResponse mockResponse = mock(CreateAccessKeyResponse.class);
        when(mockResponse.accessKey()).thenReturn(
                software.amazon.awssdk.services.iam.model.AccessKey.builder()
                        .accessKeyId("AKIA_RETRIED")
                        .secretAccessKey("secret123")
                        .build()
        );
        when(iamClient.createAccessKey(any(software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest.class))).thenReturn(mockResponse);

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.ALTO
        );

        assertTrue(result.isSuccess());
        assertEquals("AKIA_RETRIED", result.getNewKeyId());
    }

    @Test
    void executeRotation_allRetriesExhausted() {
        doThrow(new RuntimeException("Connection timeout"))
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.CRITICO
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("All 3 rotation attempts failed"));
        assertEquals(RotationState.FAIL, stateMachine.getCurrentState());

        verify(auditTrailService).logEscalation(
                eq("tenant1"), eq(testCredential.getId()), eq(com.company.rotations.models.Severidad.CRITICO),
                anyString(), eq(3)
        );
    }

    @Test
    void executeRotation_unexpectedException() {
        doThrow(new RuntimeException("Unexpected IAM error"))
                .doThrow(new RuntimeException("Unexpected IAM error"))
                .doThrow(new RuntimeException("Unexpected IAM error"))
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.BAJO
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("All 3 rotation attempts failed"));
        assertEquals(RotationState.FAIL, stateMachine.getCurrentState());
    }

    @Test
    void executeRotation_noAdminCredentials() {
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(Map.of());

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.MEDIA
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("All 3 rotation attempts failed"));
        assertEquals(RotationState.FAIL, stateMachine.getCurrentState());
    }

    @Test
    void executeRotation_usesProviderArnWhenPresent() {
        setupSuccessfulRotationMock();

        testCredential.setProviderArn("arn:aws:iam::123456789:user/rotator");

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.ALTO
        );

        assertTrue(result.isSuccess());

        verify(vaultService).storeNewCredentials(
                eq("tenant1"), eq("AKIA_NEWKEY"), anyString(), eq("AKIA_NEWKEY"),
                eq("arn:aws:iam::123456789:user/rotator")
        );
    }

    @Test
    void executeRotation_usesDefaultProviderArnWhenNull() {
        testCredential.setProviderArn(null);
        setupSuccessfulRotationMock();

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.BAJO
        );

        assertTrue(result.isSuccess());

        verify(vaultService).storeNewCredentials(
                eq("tenant1"), eq("AKIA_NEWKEY"), anyString(), eq("AKIA_NEWKEY"),
                isNull()
        );
    }

    @Test
    void executeRotation_auditsSuccessfulRotation() {
        setupSuccessfulRotationMock();

        rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.CRITICO
        );

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logActionExecuted(captor.capture());

        Map<String, Object> auditData = captor.getValue();
        assertEquals(testCredential.getId().toString(), auditData.get("alert_id"));
        assertEquals("tenant1", auditData.get("tenant_id"));
        assertEquals("AKIA1234", auditData.get("credential_id"));
        assertEquals("AKIA_NEWKEY", auditData.get("new_key_id"));
        assertEquals(true, auditData.get("success"));
    }

    @Test
    void executeRotation_auditsFailedRotation() {
        doThrow(new RuntimeException("Connection timeout"))
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.ALTO
        );

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logActionExecuted(captor.capture());

        Map<String, Object> auditData = captor.getValue();
        assertEquals(false, auditData.get("success"));
        assertEquals(3, auditData.get("attempts"));
    }

    @Test
    void executeRotation_auditFailureLogged() {
        setupSuccessfulRotationMock();
        doThrow(new RuntimeException("Audit down")).when(auditService).logActionExecuted(anyMap());

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.CRITICO
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void executeRotation_transitionLogUpdated() {
        setupSuccessfulRotationMock();

        stateMachine = new RotationStateMachine("test-rotation", testAlertId);
        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.ALTO
        );

        assertTrue(result.isSuccess());
        assertFalse(stateMachine.getTransitionLog().isEmpty());
        assertTrue(stateMachine.getCurrentState() == RotationState.SUCCESS);
    }

    @Test
    void executeRotation_setAttemptsInResult() {
        setupSuccessfulRotationMock();

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.BAJO
        );

        assertEquals(1, result.getAttempts());
    }

    @Test
    void executeRotation_failure_setsAttemptsForAllRetries() {
        doThrow(new RuntimeException("Connection timeout"))
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        RotationResult result = rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.ALTO
        );

        assertEquals(3, result.getAttempts());
    }

    @Test
    void executeRotation_stateMachineTransitionedToFailOnRetryExhaust() {
        doThrow(new RuntimeException("Connection timeout"))
                .when(iamClient).updateAccessKey(any(UpdateAccessKeyRequest.class));

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);

        rotationService.executeRotation(
                testCredential, "tenant1", stateMachine, com.company.rotations.models.Severidad.CRITICO
        );

        assertEquals(RotationState.FAIL, stateMachine.getCurrentState());
    }

    private void setupSuccessfulRotationMock() {
        CreateAccessKeyResponse mockResponse = mock(CreateAccessKeyResponse.class);
        when(mockResponse.accessKey()).thenReturn(
                software.amazon.awssdk.services.iam.model.AccessKey.builder()
                        .accessKeyId("AKIA_NEWKEY")
                        .secretAccessKey("secret123")
                        .build()
        );
        when(iamClient.createAccessKey(any(software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest.class))).thenReturn(mockResponse);

        Map<String, String> adminCreds = Map.of("arn", "arn:aws:iam::123456789:user/admin");
        when(vaultService.getAdminCredentials("tenant1")).thenReturn(adminCreds);
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
