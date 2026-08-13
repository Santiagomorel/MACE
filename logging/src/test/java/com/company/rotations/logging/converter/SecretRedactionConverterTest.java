package com.company.rotations.logging.converter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretRedactionConverterTest {

    private final SecretRedactionConverter converter = new SecretRedactionConverter();

    @Test
    void redactSecrets_redactsApiKey() {
        String input = "api_key=mySuperSecretApiKey1234567890 value";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("mySuperSecretApiKey1234567890"));
    }

    @Test
    void redactSecrets_redactsPassword() {
        String input = "password=MyP@ssw0rd!LongValue1234567890 end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("MyP@ssw0rd!LongValue1234567890"));
    }

    @Test
    void redactSecrets_redactsSecret() {
        String input = "secret=extremelylongsecretvalue12345678 end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("extremelylongsecretvalue12345678"));
    }

    @Test
    void redactSecrets_redactsToken() {
        String input = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.longvalue end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void redactSecrets_redactsAccessKey() {
        String input = "access_key=AKIAIOSFODNN7EXAMPLE1234567890 end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void redactSecrets_redactsPrivateKey() {
        String input = "private_key=privatekeyvalue1234567890abcd end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void redactSecrets_redactsApiKeyIdash() {
        String input = "api-key=longapikeyvalue1234567890abcd end";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void redactSecrets_doesNotRedactShortValues() {
        String input = "password=short value";
        String result = converter.redactSecrets(input);
        assertTrue(result.contains("short"));
        assertFalse(result.contains("[REDACTED]"));
    }

    @Test
    void redactSecrets_doesNotRedactNonSecretFields() {
        String input = "message=Hello World phase=verification";
        String result = converter.redactSecrets(input);
        assertEquals(input, result);
    }

    @Test
    void redactSecrets_handlesNull() {
        assertNull(converter.redactSecrets(null));
    }

    @Test
    void redactSecrets_handlesEmptyString() {
        assertEquals("", converter.redactSecrets(""));
    }

    @Test
    void redactSecrets_noSecrets_passthrough() {
        String input = "logger=MyLogger level=INFO timestamp=2024-01-01";
        String result = converter.redactSecrets(input);
        assertEquals(input, result);
    }

    @Test
    void convert_usesRedactSecrets() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage())
            .thenReturn("password=longpasswordvalue1234567890abcd");

        String result = converter.convert(event);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("longpasswordvalue1234567890abcd"));
    }
}
