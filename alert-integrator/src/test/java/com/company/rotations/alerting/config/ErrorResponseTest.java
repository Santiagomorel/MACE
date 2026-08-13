package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    @DisplayName("Should construct with all fields")
    void shouldConstructWithAllFields() {
        Instant now = Instant.now();
        ErrorResponse response = new ErrorResponse(
                now, 500, "INTERNAL_ERROR", "/api/v1/alerts",
                "Server error", List.of("detail-1", "detail-2")
        );

        assertEquals(now, response.timestamp());
        assertEquals(500, response.status());
        assertEquals("INTERNAL_ERROR", response.error());
        assertEquals("/api/v1/alerts", response.path());
        assertEquals("Server error", response.message());
        assertEquals(2, response.details().size());
        assertEquals("detail-1", response.details().get(0));
    }

    @Test
    @DisplayName("Should handle null details as empty list")
    void shouldHandleNullDetails() {
        ErrorResponse response = new ErrorResponse(
                Instant.now(), 400, "BAD_REQUEST", "/api/v1",
                "Bad request", null
        );

        assertEquals(0, response.details().size());
        assertNotNull(response.details());
    }
}
