package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalExceptionTest {

    @Test
    @DisplayName("Should create exception with default error code")
    void shouldCreateWithDefaultCode() {
        TechnicalException ex = new TechnicalException("DB connection failed");

        assertEquals("TECHNICAL_ERROR", ex.getErrorCode());
        assertEquals("DB connection failed", ex.getMessage());
    }

    @Test
    @DisplayName("Should create exception with cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("network error");
        TechnicalException ex = new TechnicalException("Service unavailable", cause);

        assertEquals("TECHNICAL_ERROR", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("Should create exception with cause and custom code")
    void shouldCreateWithCauseAndCode() {
        RuntimeException cause = new RuntimeException("OOM");
        TechnicalException ex = new TechnicalException("Service crash", cause, "CRASH");

        assertEquals("CRASH", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }
}
