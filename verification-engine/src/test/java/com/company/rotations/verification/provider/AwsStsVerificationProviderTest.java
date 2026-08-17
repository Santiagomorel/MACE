package com.company.rotations.verification.provider;

import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.AlertType;
import com.company.rotations.models.VerificationResult;
import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.model.PermissionMatrix;
import com.company.rotations.verification.severity.SeverityRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.ExpiredTokenException;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AwsStsVerificationProvider Tests")
@ExtendWith(MockitoExtension.class)
class AwsStsVerificationProviderTest {

    @Mock
    private StsClient stsClient;

    @Mock
    private IamClient iamClient;

    @Mock
    private PermissionEnumerator permissionEnumerator;

    @Mock
    private SeverityRuleEngine severityRuleEngine;

    @Mock
    private AuditService auditService;

    private AwsStsVerificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AwsStsVerificationProvider(
                stsClient, iamClient, permissionEnumerator, severityRuleEngine, auditService);
    }

    @Nested
    @DisplayName("Active Key Verification")
    class ActiveKeyTests {

        @Test
        @DisplayName("Should verify active credentials successfully")
        void shouldVerifyActiveKey() {
            GetCallerIdentityResponse response = mock(GetCallerIdentityResponse.class);
            when(response.account()).thenReturn("123456789012");
            when(response.arn()).thenReturn("arn:aws:iam::123456789012:user/test-user");

            when(stsClient.getCallerIdentity(any(GetCallerIdentityRequest.class))).thenReturn(response);
            when(permissionEnumerator.enumeratePermissions(any(IamClient.class), eq("arn:aws:iam::123456789012:user/test-user")))
                    .thenReturn(new PermissionMatrix());
            when(severityRuleEngine.applyFloor(any(String.class), any(SeverityRuleEngine.Severity.class)))
                    .thenReturn(SeverityRuleEngine.Severity.LOW);

            VerificationResult result = provider.verify("ACCESS_KEY",
                    Map.of("accessKey", "AKIAIOSFODNN7EXAMPLE", "secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
                    "tenant-1");

            assertNotNull(result);
            assertTrue(result.isVerified());
            assertEquals("aws", result.getProvider());
            assertEquals("LOW", result.getSeverityScope());
        }

        @Test
        @DisplayName("Should handle root account without permission enumeration")
        void shouldHandleRootAccount() {
            GetCallerIdentityResponse response = mock(GetCallerIdentityResponse.class);
            when(response.account()).thenReturn("123456789012");
            when(response.arn()).thenReturn("arn:aws:iam::123456789012:root");

            when(stsClient.getCallerIdentity(any(GetCallerIdentityRequest.class))).thenReturn(response);
            when(severityRuleEngine.applyFloor(any(String.class), any(SeverityRuleEngine.Severity.class)))
                    .thenReturn(SeverityRuleEngine.Severity.CRITICAL);

            VerificationResult result = provider.verify("ACCESS_KEY",
                    Map.of("accessKey", "AKIAIOSFODNN7EXAMPLE", "secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
                    "tenant-1");

            assertNotNull(result);
            assertTrue(result.isVerified());
            assertEquals("CRITICAL", result.getSeverityScope());
        }
    }

    @Nested
    @DisplayName("Expired Key Verification")
    class ExpiredKeyTests {

        @Test
        @DisplayName("Should return failed result for expired credentials")
        void shouldHandleExpiredKey() {
            ExpiredTokenException exception = ExpiredTokenException.builder()
                    .message("Token expired")
                    .build();

            when(stsClient.getCallerIdentity(any(GetCallerIdentityRequest.class)))
                    .thenThrow(exception);

            VerificationResult result = provider.verify("ACCESS_KEY",
                    Map.of("accessKey", "AKIAIOSFODNN7EXAMPLE", "secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
                    "tenant-1");

            assertNotNull(result);
            assertFalse(result.isVerified());
            assertEquals("LOW", result.getSeverityScope());
            assertTrue(result.getReason().contains("expired"));
        }
    }

    @Nested
    @DisplayName("Invalid Key Verification")
    class InvalidKeyTests {

        @Test
        @DisplayName("Should handle missing credentials")
        void shouldHandleMissingCredentials() {
            VerificationResult result = provider.verify("ACCESS_KEY",
                    Map.of("accessKey", "AKIAIOSFODNN7EXAMPLE"),
                    "tenant-1");

            assertNotNull(result);
            assertFalse(result.isVerified());
            assertEquals("LOW", result.getSeverityScope());
            assertTrue(result.getReason().contains("Missing"));
        }

        @Test
        @DisplayName("Should handle null credentials map")
        void shouldHandleNullCredentials() {
            VerificationResult result = provider.verify("ACCESS_KEY", null, "tenant-1");

            assertNotNull(result);
            assertFalse(result.isVerified());
            assertEquals("LOW", result.getSeverityScope());
        }
    }

    @Nested
    @DisplayName("Provider Unavailable")
    class ProviderUnavailableTests {

        @Test
        @DisplayName("Should handle network timeout errors")
        void shouldHandleNetworkError() {
            RuntimeException exception = new RuntimeException("Connect timeout");

            when(stsClient.getCallerIdentity(any(GetCallerIdentityRequest.class)))
                    .thenThrow(exception);

            VerificationResult result = provider.verify("ACCESS_KEY",
                    Map.of("accessKey", "AKIAIOSFODNN7EXAMPLE", "secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
                    "tenant-1");

            assertNotNull(result);
            assertFalse(result.isVerified());
            assertEquals("HIGH", result.getSeverityScope());
        }
    }
}
