package com.company.rotations.verification.validator;

import com.company.rotations.verification.account.mapper.AccountMapper;
import com.company.rotations.verification.cache.VerificationCacheService;
import com.company.rotations.verification.config.CircuitBreakerService;
import com.company.rotations.verification.model.ProviderType;
import com.company.rotations.verification.model.VerificationResult;
import com.company.rotations.verification.model.VerificationStatus;
import com.company.rotations.verification.provider.ProviderDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CredentialValidatorService Tests")
@ExtendWith(MockitoExtension.class)
class CredentialValidatorServiceTest {

    @Mock
    private ProviderDetector providerDetector;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AwsCredentialValidator awsValidator;

    @Mock
    private AzureCredentialValidator azureValidator;

    @Mock
    private GcpCredentialValidator gcpValidator;

    @Mock
    private VerificationCacheService cacheService;

    @Mock
    private CircuitBreakerService circuitBreakerService;

    @Mock
    private com.company.rotations.logging.service.AuditService auditService;

    private CredentialValidatorService validatorService;

    @BeforeEach
    void setUp() {
        validatorService = new CredentialValidatorService(
                providerDetector,
                accountMapper,
                awsValidator,
                azureValidator,
                gcpValidator,
                cacheService,
                circuitBreakerService,
                auditService
        );
    }

    @Nested
    @DisplayName("Cache hit path")
    class CacheHitTests {

        @Test
        @DisplayName("Should return cached result without calling validator")
        void cacheHitReturnsCachedResult() {
            VerificationResult cachedResult = VerificationResult.success(
                    "account-123", "arn:test", Collections.emptySet(), "2024-01-15");

            when(cacheService.get(anyString())).thenReturn(cachedResult);

            VerificationResult result = validatorService.verifyCredential(
                    "account-123", "credential-value", "cred-001",
                    Map.of(), Collections.emptyList());

            assertNotNull(result);
            assertEquals(VerificationStatus.VERIFIED, result.getStatus());
            verify(cacheService).get(anyString());
            verifyNoInteractions(providerDetector);
            verifyNoInteractions(accountMapper);
            verifyNoInteractions(awsValidator);
        }
    }

    @Nested
    @DisplayName("Unknown provider detection")
    class UnknownProviderTests {

        @Test
        @DisplayName("Should return UNKNOWN when provider is not detected")
        void unknownProviderReturnsUnknown() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.UNKNOWN);
            when(cacheService.get(anyString())).thenReturn(null);

            VerificationResult result = validatorService.verifyCredential(
                    "account-123", "unknown-cred-prefix", "cred-001",
                    Map.of(), Collections.emptyList());

            assertNotNull(result);
            assertEquals("UNKNOWN", result.getAccountId());
            assertEquals(VerificationStatus.UNKNOWN, result.getStatus());
            verify(cacheService).put(anyString(), any(VerificationResult.class));
        }
    }

    @Nested
    @DisplayName("Account mapping failure")
    class AccountMappingFailureTests {

        @Test
        @DisplayName("Should return UNKNOWN when account mapping fails")
        void accountMappingFailureReturnsUnknown() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(any(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn(null);

            VerificationResult result = validatorService.verifyCredential(
                    null, "AKIAIOSFODNN7EXAMPLE", "cred-001",
                    Map.of(), Collections.emptyList());

            assertNotNull(result);
            assertEquals("UNKNOWN", result.getAccountId());
            assertEquals(VerificationStatus.UNKNOWN, result.getStatus());
            verify(cacheService).put(anyString(), any(VerificationResult.class));
        }
    }

    @Nested
    @DisplayName("AWS verification path")
    class AwsVerificationTests {

        @Test
        @DisplayName("Should call AWS validator for AWS credentials")
        void awsValidatorCalledForAwsCredential() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(eq("mapped-account"), anyString(), anyString()))
                    .thenReturn(expected);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            assertNotNull(result);
            assertEquals("mapped-account", result.getAccountId());
            verify(awsValidator).validate("mapped-account", "cred-001", "AKIAIOSFODNN7EXAMPLE:secret");
            verify(cacheService).put(anyString(), any(VerificationResult.class));
        }

        @Test
        @DisplayName("Should return failed when AWS circuit breaker is open")
        void awsCircuitBreakerOpen() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(true);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            assertNotNull(result);
            assertEquals("mapped-account", result.getAccountId());
            assertEquals(VerificationStatus.INVALID, result.getStatus());
            verify(awsValidator, never()).validate(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Azure verification path")
    class AzureVerificationTests {

        @Test
        @DisplayName("Should call Azure validator for Azure credentials")
        void azureValidatorCalledForAzureCredential() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AZURE);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AZURE)))
                    .thenReturn("azure-account");
            when(circuitBreakerService.isAzureCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.failed(
                    "azure-account", "cred-002", "Deferred");
            when(azureValidator.validate(eq("azure-account"), anyString(), anyString()))
                    .thenReturn(expected);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "eyJhbGciOiJIUzI1NiJ9", "cred-002",
                    Map.of(), Arrays.asList("azure-account"));

            assertNotNull(result);
            verify(azureValidator).validate("azure-account", "cred-002", "eyJhbGciOiJIUzI1NiJ9");
        }

        @Test
        @DisplayName("Should return failed when Azure circuit breaker is open")
        void azureCircuitBreakerOpen() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AZURE);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AZURE)))
                    .thenReturn("azure-account");
            when(circuitBreakerService.isAzureCircuitOpen()).thenReturn(true);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "eyJhbGciOiJIUzI1NiJ9", "cred-002",
                    Map.of(), Arrays.asList("azure-account"));

            assertNotNull(result);
            assertEquals(VerificationStatus.INVALID, result.getStatus());
            verify(azureValidator, never()).validate(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GCP verification path")
    class GcpVerificationTests {

        @Test
        @DisplayName("Should call GCP validator for GCP credentials")
        void gcpValidatorCalledForGcpCredential() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.GCP);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.GCP)))
                    .thenReturn("gcp-account");
            when(circuitBreakerService.isGcpCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.failed(
                    "gcp-account", "cred-003", "Deferred");
            when(gcpValidator.validate(eq("gcp-account"), anyString(), anyString()))
                    .thenReturn(expected);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "AIzaSyDExample", "cred-003",
                    Map.of(), Arrays.asList("gcp-account"));

            assertNotNull(result);
            verify(gcpValidator).validate("gcp-account", "cred-003", "AIzaSyDExample");
        }

        @Test
        @DisplayName("Should return failed when GCP circuit breaker is open")
        void gcpCircuitBreakerOpen() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.GCP);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.GCP)))
                    .thenReturn("gcp-account");
            when(circuitBreakerService.isGcpCircuitOpen()).thenReturn(true);

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "AIzaSyDExample", "cred-003",
                    Map.of(), Arrays.asList("gcp-account"));

            assertNotNull(result);
            assertEquals(VerificationStatus.INVALID, result.getStatus());
            verify(gcpValidator, never()).validate(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Audit logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("Should log verification started and completed")
        void auditLogVerificationStartedAndCompleted() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(any(), any(), any())).thenReturn(expected);

            validatorService.verifyCredential(
                    "hint-account", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            verify(auditService).logVerificationStarted(anyMap());
            verify(auditService).logVerificationCompleted(anyMap());
        }

        @Test
        @DisplayName("Should handle audit logging failure gracefully")
        void auditLoggingFailureHandled() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(any(), any(), any())).thenReturn(expected);

            doThrow(new RuntimeException("Audit service down"))
                    .when(auditService).logVerificationStarted(anyMap());
            doThrow(new RuntimeException("Audit service down"))
                    .when(auditService).logVerificationCompleted(anyMap());

            VerificationResult result = validatorService.verifyCredential(
                    "hint-account", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            assertNotNull(result);
            assertEquals(VerificationStatus.VERIFIED, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Cache key building")
    class CacheKeyTests {

        @Test
        @DisplayName("Should build cache key from account hint and credential value")
        void cacheKeyBuiltCorrectly() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(any(), any(), any())).thenReturn(expected);

            validatorService.verifyCredential(
                    "account-123", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            verify(cacheService).put(eq("account-123:" + "AKIAIOSFODNN7EXAMPLE:secret".hashCode()), any());
        }

        @Test
        @DisplayName("Should use 'no-hint' when account hint is null")
        void cacheKeyNoHint() {
            when(providerDetector.detectProvider(any(), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(accountMapper.mapAccount(any(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(any(), any(), any())).thenReturn(expected);

            validatorService.verifyCredential(
                    null, "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of(), Arrays.asList("mapped-account"));

            verify(cacheService).put(eq("no-hint:" + "AKIAIOSFODNN7EXAMPLE:secret".hashCode()), any());
        }
    }

    @Nested
    @DisplayName("Provider name extraction")
    class ProviderNameExtractionTests {

        @Test
        @DisplayName("Should use provider from payload when available")
        void providerFromPayload() {
            when(providerDetector.detectProvider(eq("aws"), anyString()))
                    .thenReturn(ProviderType.AWS);
            when(cacheService.get(anyString())).thenReturn(null);
            when(accountMapper.mapAccount(anyString(), anyString(), anyList(), eq(ProviderType.AWS)))
                    .thenReturn("mapped-account");
            when(circuitBreakerService.isAwsCircuitOpen()).thenReturn(false);

            VerificationResult expected = VerificationResult.success(
                    "mapped-account", "arn:aws:test", Collections.emptySet(), "2024-01-15");
            when(awsValidator.validate(any(), any(), any())).thenReturn(expected);

            validatorService.verifyCredential(
                    "hint", "AKIAIOSFODNN7EXAMPLE:secret", "cred-001",
                    Map.of("provider", "aws"), Arrays.asList("mapped-account"));

            verify(providerDetector).detectProvider("aws", "AKIAIOSFODNN7EXAMPLE:secret");
        }
    }
}
