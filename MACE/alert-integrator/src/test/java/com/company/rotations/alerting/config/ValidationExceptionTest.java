package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationExceptionTest {

    @Test
    @DisplayName("Should create with field and message")
    void shouldCreateWithFieldAndMessage() {
        ValidationException ex = new ValidationException("email", "Invalid email format");

        assertEquals("email", ex.getField());
        assertEquals("Invalid email format", ex.getMessage());
    }

    @Test
    @DisplayName("Should create with message only")
    void shouldCreateWithMessageOnly() {
        ValidationException ex = new ValidationException("Required field missing");

        assertNull(ex.getField());
        assertEquals("Required field missing", ex.getMessage());
    }
}
