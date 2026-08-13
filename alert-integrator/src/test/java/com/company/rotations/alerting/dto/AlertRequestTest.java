package com.company.rotations.alerting.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertRequestTest {

    @Test
    @DisplayName("Should construct with all fields")
    void shouldConstructWithAllFields() {
        AlertRequest request = new AlertRequest(
                "gitguardian",
                "AWS_ACCESS_KEY",
                "tenant-1",
                "my-repo",
                "{\"raw\":\"data\"}"
        );

        assertEquals("gitguardian", request.providerName());
        assertEquals("AWS_ACCESS_KEY", request.credentialType());
        assertEquals("tenant-1", request.tenantId());
        assertEquals("my-repo", request.repository());
        assertEquals("{\"raw\":\"data\"}", request.rawPayload());
    }

    @Test
    @DisplayName("Should handle null optional fields")
    void shouldHandleNullOptionalFields() {
        AlertRequest request = new AlertRequest(
                "gitguardian",
                "GENERIC",
                "tenant-1",
                null,
                null
        );

        assertEquals("gitguardian", request.providerName());
        assertEquals("GENERIC", request.credentialType());
        assertEquals("tenant-1", request.tenantId());
        assertNull(request.repository());
        assertNull(request.rawPayload());
    }
}
