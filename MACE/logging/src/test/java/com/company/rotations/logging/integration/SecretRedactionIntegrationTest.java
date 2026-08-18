package com.company.rotations.logging.integration;

import com.company.rotations.logging.converter.SecretRedactionConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integration Tests - Secret Redaction")
class SecretRedactionIntegrationTest {

    private SecretRedactionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SecretRedactionConverter();
    }

    @Test
    @DisplayName("Redacts secret values with equals sign separator")
    void testRedactsSecretWithEquals() {
        String input = "Connecting with secret=SuperSecretValue1234567890 to database";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("SuperSecretValue1234567890"));
        assertTrue(output.contains("[REDACTED]"));
        assertEquals("Connecting with [REDACTED] to database", output);
    }

    @Test
    @DisplayName("Redacts secret values with colon separator")
    void testRedactsSecretWithColon() {
        String input = "Configuration: secret: MySuperSecretValue1234567890";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("MySuperSecretValue1234567890"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts password values")
    void testRedactsPassword() {
        String input = "User login with password=P@ssw0rd!SuperLongValue12345";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("P@ssw0rd!SuperLongValue12345"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts API key values with underscore")
    void testRedactsApiKeyUnderscore() {
        String input = "API key configuration: api_key=abcdef1234567890abcdef1234567890";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("abcdef1234567890abcdef1234567890"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts API key values with hyphen")
    void testRedactsApiKeyHyphen() {
        String input = "Using api-key=MyHyphenatedApiKey1234567890ab for auth";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("MyHyphenatedApiKey1234567890ab"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts access key values")
    void testRedactsAccessKey() {
        String input = "AWS access_key=AKIAIOSFODNN7EXAMPLE1234567890 configured";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("AKIAIOSFODNN7EXAMPLE1234567890"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts private key values")
    void testRedactsPrivateKey() {
        String input = "Private key: private_key=SuperPrivateKeyValue1234567890abcd";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("SuperPrivateKeyValue1234567890abcd"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts token values")
    void testRedactsToken() {
        String input = "Auth token=BearerSuperLongTokenValue1234567890abcdef";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("BearerSuperLongTokenValue1234567890abcdef"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Redacts generic key values")
    void testRedactsGenericKey() {
        String input = "Setting key=MyGenericKeyValue1234567890abcdef in config";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("MyGenericKeyValue1234567890abcdef"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Does not redact short secret values")
    void testDoesNotRedactShortValues() {
        String input = "Using secret=short123 for testing";
        String output = converter.redactSecrets(input);

        assertTrue(output.contains("short123"));
        assertFalse(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Does not redact password values shorter than minimum length")
    void testDoesNotRedactShortPassword() {
        String input = "Password is password=abc123";
        String output = converter.redactSecrets(input);

        assertTrue(output.contains("abc123"));
    }

    @Test
    @DisplayName("Preserves text without secrets")
    void testPreservesTextWithoutSecrets() {
        String input = "Application started successfully on port 8080";
        String output = converter.redactSecrets(input);

        assertEquals(input, output);
        assertFalse(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Handles null input gracefully")
    void testHandlesNullInput() {
        String output = converter.redactSecrets(null);
        assertNull(output);
    }

    @Test
    @DisplayName("Handles empty input gracefully")
    void testHandlesEmptyInput() {
        String output = converter.redactSecrets("");
        assertEquals("", output);
    }

    @Test
    @DisplayName("Redacts multiple secrets in single message")
    void testRedactsMultipleSecrets() {
        String input = "Config: secret=SuperSecretValue1234567890 and password=SuperPasswordValue1234567890ab";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("SuperSecretValue1234567890"));
        assertFalse(output.contains("SuperPasswordValue1234567890ab"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Case insensitive secret detection for SECRET")
    void testCaseInsensitiveSecretDetection() {
        String input = "Using SECRET=SuperSecretValue1234567890 uppercase";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("SuperSecretValue1234567890"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Case insensitive secret detection for Password")
    void testCaseInsensitivePasswordDetection() {
        String input = "Login with Password=SuperPasswordValue1234567890ab mixed case";
        String output = converter.redactSecrets(input);

        assertFalse(output.contains("SuperPasswordValue1234567890ab"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Converter produces expected output in logging event context")
    void testConverterInLoggingContext() {
        ILoggingEvent mockEvent = Mockito.mock(ILoggingEvent.class);
        Mockito.when(mockEvent.getFormattedMessage())
                .thenReturn("Connecting with secret=SuperSecretValue1234567890 to database");

        String output = converter.convert(mockEvent);

        assertFalse(output.contains("SuperSecretValue1234567890"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Converter handles null logging event message")
    void testConverterHandlesNullMessage() {
        ILoggingEvent mockEvent = Mockito.mock(ILoggingEvent.class);
        Mockito.when(mockEvent.getFormattedMessage()).thenReturn(null);

        String output = converter.convert(mockEvent);
        assertNull(output);
    }

    @Test
    @DisplayName("Redaction boundary at exactly 20 characters")
    void testRedactionBoundaryAt20Characters() {
        // Exactly 20 characters - should be redacted
        String exactly20 = "secret=" + "A".repeat(20);
        String output20 = converter.redactSecrets(exactly20);
        assertTrue(output20.contains("[REDACTED]"));

        // 19 characters - should NOT be redacted
        String oneBelow = "secret=" + "B".repeat(19);
        String output19 = converter.redactSecrets(oneBelow);
        assertTrue(output19.contains("BBBBBBBBBBBBBBBBBBB"));
        assertFalse(output19.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Handles message with no secret patterns")
    void testHandlesNoSecretPatterns() {
        String input = "Application running normally with no sensitive data";
        String output = converter.redactSecrets(input);

        assertEquals(input, output);
    }
}
