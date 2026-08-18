package com.company.rotations.verification.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VerificationResult Tests")
class VerificationResultTest {

    private VerificationResult result;

    @BeforeEach
    void setUp() {
        result = new VerificationResult();
    }

    @Nested
    @DisplayName("Success factory method")
    class SuccessFactoryTests {

        @Test
        @DisplayName("Should create success result with correct status")
        void successCreatesVerifiedStatus() {
            VerificationResult r = VerificationResult.success(
                    "account-123",
                    "arn:aws:iam::123456789012:user/test-user",
                    Set.of("s3:GetObject", "s3:PutObject"),
                    "2024-01-15"
            );

            assertNotNull(r);
            assertEquals(VerificationStatus.VERIFIED, r.getStatus());
            assertEquals("account-123", r.getAccountId());
            assertEquals("arn:aws:iam::123456789012:user/test-user", r.getIdentityArn());
            assertEquals(Set.of("s3:GetObject", "s3:PutObject"), r.getActionMatrix());
            assertEquals("2024-01-15", r.getLastUsedDate());
            assertNotNull(r.getVerifiedAt());
        }
    }

    @Nested
    @DisplayName("Failed factory method")
    class FailedFactoryTests {

        @Test
        @DisplayName("Should create failed result with correct status and reason")
        void failedCreatesInvalidStatus() {
            VerificationResult r = VerificationResult.failed(
                    "account-456",
                    "cred-001",
                    "Expired credentials"
            );

            assertNotNull(r);
            assertEquals(VerificationStatus.INVALID, r.getStatus());
            assertEquals("account-456", r.getAccountId());
            assertEquals("cred-001", r.getIdentityArn());
            assertEquals("Expired credentials", r.getReason());
            assertEquals("never", r.getLastUsedDate());
            assertNotNull(r.getVerifiedAt());
        }
    }

    @Nested
    @DisplayName("Rate limited factory method")
    class RateLimitedFactoryTests {

        @Test
        @DisplayName("Should create rate limited result with correct status")
        void rateLimitedCreatesRateLimitedStatus() {
            VerificationResult r = VerificationResult.rateLimited(
                    "account-789",
                    "cred-002"
            );

            assertNotNull(r);
            assertEquals(VerificationStatus.RATE_LIMITED, r.getStatus());
            assertEquals("account-789", r.getAccountId());
            assertEquals("cred-002", r.getIdentityArn());
            assertEquals("unknown", r.getLastUsedDate());
            assertNotNull(r.getVerifiedAt());
        }
    }

    @Nested
    @DisplayName("Unknown account factory method")
    class UnknownAccountFactoryTests {

        @Test
        @DisplayName("Should create unknown account result with correct status")
        void unknownAccountCreatesUnknownStatus() {
            VerificationResult r = VerificationResult.unknownAccount("cred-999");

            assertNotNull(r);
            assertEquals(VerificationStatus.UNKNOWN, r.getStatus());
            assertEquals("UNKNOWN", r.getAccountId());
            assertEquals("cred-999", r.getIdentityArn());
            assertEquals("unknown", r.getLastUsedDate());
            assertEquals("Account could not be mapped to any known client account", r.getReason());
            assertNotNull(r.getVerifiedAt());
        }
    }

    @Nested
    @DisplayName("Getter and setter operations")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get all fields")
        void setAndGetFields() {
            result.setAccountId("account-123");
            result.setIdentityArn("arn:aws:iam::123456789012:user/test");
            result.setStatus(VerificationStatus.VERIFIED);
            result.setActionMatrix(Set.of("s3:GetObject"));
            result.setLastUsedDate("2024-01-15");
            result.setVerifiedAt(java.time.Instant.now());
            result.setReason("test reason");

            assertEquals("account-123", result.getAccountId());
            assertEquals("arn:aws:iam::123456789012:user/test", result.getIdentityArn());
            assertEquals(VerificationStatus.VERIFIED, result.getStatus());
            assertEquals(Set.of("s3:GetObject"), result.getActionMatrix());
            assertEquals("2024-01-15", result.getLastUsedDate());
            assertEquals("test reason", result.getReason());
        }

        @Test
        @DisplayName("Should handle null values in getters")
        void nullValues() {
            assertNull(result.getAccountId());
            assertNull(result.getIdentityArn());
            assertNull(result.getStatus());
            assertNull(result.getActionMatrix());
            assertNull(result.getLastUsedDate());
            assertNull(result.getVerifiedAt());
            assertNull(result.getReason());
        }
    }

    @Nested
    @DisplayName("Jackson annotation compatibility")
    class JacksonAnnotationTests {

        @Test
        @DisplayName("Should have identity_arn property annotation")
        void identityArnJsonProperty() {
            result.setIdentityArn("arn:aws:iam::123456789012:user/test");
            assertEquals("arn:aws:iam::123456789012:user/test", result.getIdentityArn());
        }

        @Test
        @DisplayName("Should have action_matrix property annotation")
        void actionMatrixJsonProperty() {
            result.setActionMatrix(Set.of("s3:GetObject"));
            assertEquals(Set.of("s3:GetObject"), result.getActionMatrix());
        }
    }
}
