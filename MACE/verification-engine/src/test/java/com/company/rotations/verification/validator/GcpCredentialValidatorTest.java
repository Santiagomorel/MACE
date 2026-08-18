package com.company.rotations.verification.validator;

import com.company.rotations.verification.model.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GcpCredentialValidator Tests")
class GcpCredentialValidatorTest {

    private GcpCredentialValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GcpCredentialValidator();
    }

    @Nested
    @DisplayName("Deferred validation")
    class DeferredValidationTests {

        @Test
        @DisplayName("Should return failed result with deferred message")
        void returnsDeferredResult() {
            VerificationResult result = validator.validate(
                    "account-123", "gcp-cred-001", "AIzaSyDExample");

            assertNotNull(result);
            assertEquals(VerificationResult.class, result.getClass());
            assertEquals("account-123", result.getAccountId());
            assertEquals("gcp-cred-001", result.getIdentityArn());
            assertEquals("GCP verification is deferred - not in scope for Release 1", result.getReason());
        }

        @Test
        @DisplayName("Should return failed for empty credential value")
        void returnsFailedForEmptyCredential() {
            VerificationResult result = validator.validate(
                    "account-123", "gcp-cred-001", "");

            assertNotNull(result);
            assertEquals("GCP verification is deferred - not in scope for Release 1", result.getReason());
        }

        @Test
        @DisplayName("Should return failed for null credential value")
        void returnsFailedForNullCredential() {
            VerificationResult result = validator.validate(
                    "account-123", "gcp-cred-001", null);

            assertNotNull(result);
            assertEquals("GCP verification is deferred - not in scope for Release 1", result.getReason());
        }
    }
}
