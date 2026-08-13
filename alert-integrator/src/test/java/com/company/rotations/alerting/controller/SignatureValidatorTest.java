package com.company.rotations.alerting.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class SignatureValidatorTest {

    private static final String TEST_SECRET = "my-shared-secret";
    private static final String TEST_HEADER = "X-GitGuardian-Signature";

    private SignatureValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new SignatureValidator(TEST_SECRET, TEST_HEADER);
    }

    @Nested
    @DisplayName("Signature Validation")
    class SignatureValidationTests {

        @Test
        @DisplayName("Should return true for valid signature")
        void shouldReturnTrueForValidSignature() throws Exception {
            String payload = "test payload data";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String validSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

            assertTrue(validator.isValid(payload, validSignature, "gitguardian"));
        }

        @Test
        @DisplayName("Should return true for valid signature with uppercase")
        void shouldReturnTrueForValidSignatureUppercase() throws Exception {
            String payload = "test payload data";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String validSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).toUpperCase();

            assertTrue(validator.isValid(payload, validSignature, "gitguardian"));
        }

        @Test
        @DisplayName("Should return false for invalid signature")
        void shouldReturnFalseForInvalidSignature() {
            assertFalse(validator.isValid("test payload", "invalidsignature", "gitguardian"));
        }

        @Test
        @DisplayName("Should return false for missing signature")
        void shouldReturnFalseForMissingSignature() {
            assertFalse(validator.isValid("test payload", null, "gitguardian"));
        }

        @Test
        @DisplayName("Should return false for blank signature")
        void shouldReturnFalseForBlankSignature() {
            assertFalse(validator.isValid("test payload", "   ", "gitguardian"));
        }
    }

    @Nested
    @DisplayName("Skipped Validation")
    class SkippedValidationTests {

        @Test
        @DisplayName("Should return true when shared secret is changeme")
        void shouldReturnTrueWhenSecretIsChangeme() {
            SignatureValidator disabledValidator = new SignatureValidator("changeme", TEST_HEADER);
            assertTrue(disabledValidator.isValid("any payload", "any signature", "gitguardian"));
        }

        @Test
        @DisplayName("Should return true when shared secret is blank")
        void shouldReturnTrueWhenSecretIsBlank() {
            SignatureValidator blankSecretValidator = new SignatureValidator("", TEST_HEADER);
            assertTrue(blankSecretValidator.isValid("any payload", "any signature", "gitguardian"));
        }

        @Test
        @DisplayName("Should return true when shared secret is null")
        void shouldReturnTrueWhenSecretIsNull() {
            SignatureValidator nullSecretValidator = new SignatureValidator(null, TEST_HEADER);
            assertTrue(nullSecretValidator.isValid("any payload", "any signature", "gitguardian"));
        }
    }

    @Nested
    @DisplayName("Header Name")
    class HeaderNameTests {

        @Test
        @DisplayName("Should return custom header name")
        void shouldReturnCustomHeaderName() {
            assertEquals("X-GitGuardian-Signature", validator.getSignatureHeaderName());
        }

        @Test
        @DisplayName("Should return different header name when configured")
        void shouldReturnDifferentHeaderName() {
            SignatureValidator customHeaderValidator = new SignatureValidator(TEST_SECRET, "X-Custom-Signature");
            assertEquals("X-Custom-Signature", customHeaderValidator.getSignatureHeaderName());
        }
    }

    @Nested
    @DisplayName("Different Sources")
    class SourceTests {

        @Test
        @DisplayName("Should validate for different sources")
        void shouldValidateForDifferentSources() throws Exception {
            String payload = "test";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String validSig = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

            assertTrue(validator.isValid(payload, validSig, "source-a"));
            assertTrue(validator.isValid(payload, validSig, "source-b"));
        }
    }
}
