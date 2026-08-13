package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    @DisplayName("Should create exception with message and default error code")
    void shouldCreateWithDefaultCode() {
        BusinessException ex = new BusinessException("Something went wrong");

        assertEquals("BUSINESS_ERROR", ex.getErrorCode());
        assertEquals("Something went wrong", ex.getMessage());
    }

    @Test
    @DisplayName("Should create exception with custom error code")
    void shouldCreateWithCustomCode() {
        BusinessException ex = new BusinessException("Error", "CUSTOM_CODE");

        assertEquals("CUSTOM_CODE", ex.getErrorCode());
    }

    @Test
    @DisplayName("Should create exception with cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException("Wrapper", cause);

        assertEquals("BUSINESS_ERROR", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("Should create exception with cause and error code")
    void shouldCreateWithCauseAndCode() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException("Wrapper", cause, "DEEP_ERROR");

        assertEquals("DEEP_ERROR", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }
}
