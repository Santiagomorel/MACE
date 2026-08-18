package com.company.rotations.verification.validator;

import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.model.PermissionMatrix;
import com.company.rotations.verification.model.VerificationResult;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
import software.amazon.awssdk.services.sts.model.StsException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AwsCredentialValidator Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AwsCredentialValidatorTest {

    @Mock
    private PermissionEnumerator permissionEnumerator;

    @Mock
    private RetryRegistry retryRegistry;

    @Mock
    private Retry retry;

    private AwsCredentialValidator validator;

    @BeforeEach
    void setUp() {
        when(retryRegistry.retry("awsApiRetry")).thenReturn(retry);
        when(retryRegistry.retry("iamApiRetry")).thenReturn(retry);
        validator = new AwsCredentialValidator(permissionEnumerator, retryRegistry, "us-east-1");
    }

    @Nested
    @DisplayName("Empty and invalid credentials")
    class EmptyInvalidCredentialTests {

        @Test
        @DisplayName("Should return failed for null credential value")
        void nullCredentialValue() {
            VerificationResult result = validator.validate("account-123", "cred-001", null);

            assertEquals("account-123", result.getAccountId());
            assertEquals("Empty credential value", result.getReason());
        }

        @Test
        @DisplayName("Should return failed for blank credential value")
        void blankCredentialValue() {
            VerificationResult result = validator.validate("account-123", "cred-001", "   ");

            assertEquals("account-123", result.getAccountId());
            assertEquals("Empty credential value", result.getReason());
        }

        @Test
        @DisplayName("Should return failed for single-part credential")
        void singlePartCredential() {
            VerificationResult result = validator.validate("account-123", "cred-001", "onlyonepart");

            assertEquals("account-123", result.getAccountId());
            assertEquals("Invalid AWS credential format", result.getReason());
        }

        @Test
        @DisplayName("Should return failed for three-part credential")
        void threePartCredential() {
            VerificationResult result = validator.validate("account-123", "cred-001", "part1:part2:part3");

            assertEquals("account-123", result.getAccountId());
            assertEquals("Invalid AWS credential format", result.getReason());
        }
    }

    @Nested
    @DisplayName("Successful STS verification")
    class SuccessfulVerificationTests {

        @Test
        @DisplayName("Should return success with valid credentials")
        void validCredentialsSuccess() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:secretkey123";

            GetCallerIdentityResponse identityResponse = mock(GetCallerIdentityResponse.class);
            when(identityResponse.arn()).thenReturn("arn:aws:iam::123456789012:user/test-user");
            when(identityResponse.account()).thenReturn("123456789012");
            when(retry.executeSupplier(any())).thenReturn(identityResponse);

            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("s3:GetObject");
            when(permissionEnumerator.enumeratePermissions(any(IamClient.class), any(String.class)))
                    .thenReturn(matrix);

            VerificationResult result = validator.validate("account-123", "cred-001", credentialValue);

            assertNotNull(result);
            assertEquals("123456789012", result.getAccountId());
            assertEquals("arn:aws:iam::123456789012:user/test-user", result.getIdentityArn());
            assertEquals(Set.of("s3:GetObject"), result.getActionMatrix());
        }

        @Test
        @DisplayName("Should use account hint when STS returns null account")
        void nullAccountFromStsUsesHint() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:secretkey123";

            GetCallerIdentityResponse identityResponse = mock(GetCallerIdentityResponse.class);
            when(identityResponse.arn()).thenReturn("arn:aws:iam::123456789012:user/test-user");
            when(identityResponse.account()).thenReturn(null);
            when(retry.executeSupplier(any())).thenReturn(identityResponse);

            PermissionMatrix matrix = new PermissionMatrix();
            when(permissionEnumerator.enumeratePermissions(any(IamClient.class), any(String.class)))
                    .thenReturn(matrix);

            VerificationResult result = validator.validate("account-123", "cred-001", credentialValue);

            assertNotNull(result);
            assertEquals("account-123", result.getAccountId());
        }
    }

    @SuppressWarnings("unchecked")
    private StsException createStsException(String errorCode, String errorMessage) {
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
        return (StsException) (Object) StsException.builder()
                .message(errorCode + ": " + errorMessage)
                .awsErrorDetails(errorDetails)
                .build();
    }

    @Nested
    @DisplayName("STS exception handling")
    class StsExceptionTests {

        @Test
        @DisplayName("Should handle ExpiredTokenException")
        void expiredTokenException() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:expiredkey";

            StsException stsException = createStsException("ExpiredTokenException", "The security token included has expired");

            when(retry.executeSupplier(any())).thenThrow(stsException);

            VerificationResult result = validator.validate("account-123", "cred-001", credentialValue);

            assertNotNull(result);
            assertEquals("account-123", result.getAccountId());
            assertEquals("Expired credentials", result.getReason());
        }

        @Test
        @DisplayName("Should handle InvalidClientTokenId")
        void invalidClientTokenId() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:badkey";

            StsException stsException = createStsException("InvalidClientTokenId", "The security token included is not valid");

            when(retry.executeSupplier(any())).thenThrow(stsException);

            VerificationResult result = validator.validate("account-456", "cred-002", credentialValue);

            assertNotNull(result);
            assertEquals("account-456", result.getAccountId());
            assertEquals("Invalid credentials", result.getReason());
        }

        @Test
        @DisplayName("Should handle AccessDenied")
        void accessDenied() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:limitedkey";

            StsException stsException = createStsException("AccessDenied", "User is not authorized");

            when(retry.executeSupplier(any())).thenThrow(stsException);

            VerificationResult result = validator.validate("account-789", "cred-003", credentialValue);

            assertNotNull(result);
            assertEquals("account-789", result.getAccountId());
            assertEquals("AccessDenied - insufficient permissions", result.getReason());
        }

        @Test
        @DisplayName("Should handle unhandled STS error codes")
        void unhandledStsError() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:unknownerr";

            StsException stsException = createStsException("SomeUnknownError", "Something went wrong");

            when(retry.executeSupplier(any())).thenThrow(stsException);

            VerificationResult result = validator.validate("account-999", "cred-004", credentialValue);

            assertNotNull(result);
            assertEquals("account-999", result.getAccountId());
            assertTrue(result.getReason().startsWith("STS error:"));
            assertTrue(result.getReason().contains("SomeUnknownError"));
        }

        @Test
        @DisplayName("Should handle StsException without awsErrorDetails")
        void stsExceptionNoDetails() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:nodetails";

            @SuppressWarnings("unchecked")
            StsException stsException = (StsException) (Object) StsException.builder()
                    .message("Unknown error with no details")
                    .build();

            when(retry.executeSupplier(any())).thenThrow(stsException);

            VerificationResult result = validator.validate("account-000", "cred-005", credentialValue);

            assertNotNull(result);
            assertEquals("account-000", result.getAccountId());
            assertTrue(result.getReason().startsWith("STS error:"));
        }
    }

    @Nested
    @DisplayName("Permission enumeration failure handling")
    class PermissionEnumerationTests {

        @Test
        @DisplayName("Should return partial success when permission enumeration fails")
        void permissionEnumerationFails() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:secretkey123";

            GetCallerIdentityResponse identityResponse = mock(GetCallerIdentityResponse.class);
            when(identityResponse.arn()).thenReturn("arn:aws:iam::123456789012:user/test-user");
            when(identityResponse.account()).thenReturn("123456789012");
            when(retry.executeSupplier(any())).thenReturn(identityResponse);

            when(permissionEnumerator.enumeratePermissions(any(IamClient.class), any(String.class)))
                    .thenThrow(new RuntimeException("IAM API error"));

            VerificationResult result = validator.validate("account-123", "cred-001", credentialValue);

            assertNotNull(result);
            assertEquals("123456789012", result.getAccountId());
            assertEquals("arn:aws:iam::123456789012:user/test-user", result.getIdentityArn());
            assertEquals(Collections.emptySet(), result.getActionMatrix());
        }
    }

    @Nested
    @DisplayName("Unexpected exceptions")
    class UnexpectedExceptionTests {

        @Test
        @DisplayName("Should handle unexpected exceptions during validation")
        void unexpectedExceptionDuringValidation() {
            String credentialValue = "AKIAIOSFODNN7EXAMPLE:badkey";

            when(retry.executeSupplier(any())).thenThrow(new RuntimeException("Unexpected network failure"));

            VerificationResult result = validator.validate("account-123", "cred-001", credentialValue);

            assertNotNull(result);
            assertEquals("account-123", result.getAccountId());
            assertTrue(result.getReason().startsWith("Unexpected error:"));
            assertTrue(result.getReason().contains("Unexpected network failure"));
        }
    }
}
