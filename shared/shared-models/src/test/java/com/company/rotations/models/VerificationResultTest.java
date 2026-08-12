package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VerificationResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    VerificationResultTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateVerificationResult() {
        UUID alertId = UUID.randomUUID();

        VerificationResult result = new VerificationResult(
                alertId, true, "credentials_valid",
                "high", "medium", AlertType.AWS_ACCESS_KEY, "tenant-1", "aws");

        assertEquals(alertId, result.getAlertId());
        assertTrue(result.isVerified());
        assertEquals("credentials_valid", result.getReason());
        assertEquals("high", result.getSeverityScope());
        assertEquals("medium", result.getBlastRadius());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        UUID alertId = UUID.randomUUID();
        VerificationResult result = new VerificationResult(
                alertId, false, "key_expired",
                "low", "none", AlertType.IAM_USER, "tenant-2", "aws");

        String json = mapper.writeValueAsString(result);
        VerificationResult deserialized = mapper.readValue(json, VerificationResult.class);

        assertEquals(alertId, deserialized.getAlertId());
        assertFalse(deserialized.isVerified());
        assertEquals("key_expired", deserialized.getReason());
    }
}
