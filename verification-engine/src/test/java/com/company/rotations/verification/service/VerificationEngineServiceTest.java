package com.company.rotations.verification.service;

import com.company.rotations.verification.adapter.AlertInputAdapter;
import com.company.rotations.verification.account.mapper.AccountMapper;
import com.company.rotations.verification.cache.VerificationCacheService;
import com.company.rotations.verification.config.CircuitBreakerService;
import com.company.rotations.verification.model.ProviderType;
import com.company.rotations.verification.model.VerificationResult;
import com.company.rotations.verification.provider.ProviderDetector;
import com.company.rotations.verification.validator.AwsCredentialValidator;
import com.company.rotations.verification.validator.AzureCredentialValidator;
import com.company.rotations.verification.validator.CredentialValidatorService;
import com.company.rotations.verification.validator.GcpCredentialValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("VerificationEngineService Integration Tests")
@ExtendWith(MockitoExtension.class)
class VerificationEngineServiceTest {

    @Mock
    private AwsCredentialValidator awsValidator;

    @Mock
    private AzureCredentialValidator azureValidator;

    @Mock
    private GcpCredentialValidator gcpValidator;

    private VerificationEngineService engineService;

    @BeforeEach
    void setUp() {
        AlertInputAdapter inputAdapter = new AlertInputAdapter();

        ProviderDetector providerDetector = new ProviderDetector();
        AccountMapper accountMapper = new AccountMapper();

        lenient().doReturn(VerificationResult.failed("default-account", "cred", "Azure verification is deferred"))
                .when(azureValidator).validate(any(), any(), any());
        lenient().doReturn(VerificationResult.failed("default-account", "cred", "GCP verification is deferred"))
                .when(gcpValidator).validate(any(), any(), any());

        CredentialValidatorService validatorService = new CredentialValidatorService(
                providerDetector,
                accountMapper,
                awsValidator,
                azureValidator,
                gcpValidator,
                mock(VerificationCacheService.class),
                mock(CircuitBreakerService.class)
        );

        engineService = new VerificationEngineService(inputAdapter, validatorService);
    }

    @Test
    @DisplayName("End-to-end: AWS credential alert produces verification result")
    void testAwsCredentialFlow() {
        when(awsValidator.validate(eq("client-account-456"), any(), any()))
                .thenReturn(VerificationResult.failed("client-account-456", "AKIAIOSFODNN7EXAMPLE", "Test failure"));

        Map<String, Object> payload = Map.of(
                "event_id", "evt-123",
                "source", "gitguardian",
                "account_hint", "client-account-456",
                "credential_value", "AKIAIOSFODNN7EXAMPLE:secretKey123",
                "provider", "aws"
        );

        VerificationResult result = engineService.processAlert(payload, Arrays.asList("client-account-456"));

        assertNotNull(result);
        assertEquals("client-account-456", result.getAccountId());
    }

    @Test
    @DisplayName("End-to-end: Azure credential alert returns deferred result")
    void testAzureCredentialFlow() {
        Map<String, Object> payload = Map.of(
                "event_id", "evt-456",
                "source", "gitguardian",
                "account_hint", "client-account-789",
                "credential_value", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
                "provider", "azure"
        );

        VerificationResult result = engineService.processAlert(payload, Arrays.asList("client-account-789"));

        assertNotNull(result);
    }

    @Test
    @DisplayName("End-to-end: GCP credential alert returns deferred result")
    void testGcpCredentialFlow() {
        Map<String, Object> payload = Map.of(
                "event_id", "evt-789",
                "source", "gitguardian",
                "credential_value", "AIzaSyExampleKey1234567890",
                "provider", "gcp"
        );

        VerificationResult result = engineService.processAlert(payload, Arrays.asList());

        assertNotNull(result);
    }

    @Test
    @DisplayName("End-to-end: Unknown provider returns UNKNOWN result")
    void testUnknownProviderFlow() {
        Map<String, Object> payload = Map.of(
                "event_id", "evt-999",
                "source", "gitguardian",
                "credential_value", "XYZunknownPrefix123",
                "provider", "unknown"
        );

        VerificationResult result = engineService.processAlert(payload, Arrays.asList());

        assertNotNull(result);
        assertEquals("UNKNOWN", result.getAccountId());
    }
}
