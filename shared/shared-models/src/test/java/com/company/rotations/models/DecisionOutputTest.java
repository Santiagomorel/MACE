package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DecisionOutputTest {

    private final ObjectMapper mapper = new ObjectMapper();

    DecisionOutputTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateDecisionOutput() {
        UUID alertId = UUID.randomUUID();

        DecisionOutput output = new DecisionOutput(
                alertId, "rotate", "high", "verified_credentials_at_risk",
                "aws-key-rotation");

        assertEquals(alertId, output.getAlertId());
        assertEquals("rotate", output.getDecision());
        assertEquals("high", output.getSeverity());
        assertEquals("aws-key-rotation", output.getPlaybookName());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        UUID alertId = UUID.randomUUID();
        DecisionOutput output = new DecisionOutput(
                alertId, "no_action", "low", "credential_expired", null);

        String json = mapper.writeValueAsString(output);
        DecisionOutput deserialized = mapper.readValue(json, DecisionOutput.class);

        assertEquals(alertId, deserialized.getAlertId());
        assertEquals("no_action", deserialized.getDecision());
        assertNull(deserialized.getPlaybookName());
    }
}
