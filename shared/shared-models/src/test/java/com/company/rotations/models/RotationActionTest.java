package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RotationActionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    RotationActionTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateRotationAction() {
        UUID alertId = UUID.randomUUID();

        RotationAction action = new RotationAction(alertId, AlertType.AWS_ACCESS_KEY, "aws");

        assertEquals(alertId, action.getAlertId());
        assertEquals(AlertType.AWS_ACCESS_KEY, action.getCredentialType());
        assertEquals("aws", action.getProvider());
        assertEquals("PENDING", action.getStatus());
        assertEquals(0, action.getAttempts());
        assertNotNull(action.getCreatedAt());
        assertNotNull(action.getUpdatedAt());
    }

    @Test
    void shouldUpdateStatus() {
        UUID alertId = UUID.randomUUID();
        RotationAction action = new RotationAction(alertId, AlertType.IAM_USER, "aws");

        action.setStatus("ROTATING");
        assertEquals("ROTATING", action.getStatus());

        action.setStatus("SUCCESS");
        assertEquals("SUCCESS", action.getStatus());
    }

    @Test
    void shouldIncrementAttempts() {
        UUID alertId = UUID.randomUUID();
        RotationAction action = new RotationAction(alertId, AlertType.AWS_ACCESS_KEY, "aws");

        action.setAttempts(1);
        action.setAttempts(2);
        assertEquals(2, action.getAttempts());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        UUID alertId = UUID.randomUUID();
        RotationAction action = new RotationAction(alertId, AlertType.RDS_CREDENTIAL, "aws");
        action.setStatus("ROTATING");
        action.setAttempts(3);
        action.setTimeout(300000L);

        String json = mapper.writeValueAsString(action);
        RotationAction deserialized = mapper.readValue(json, RotationAction.class);

        assertEquals(alertId, deserialized.getAlertId());
        assertEquals(AlertType.RDS_CREDENTIAL, deserialized.getCredentialType());
        assertEquals("ROTATING", deserialized.getStatus());
        assertEquals(3, deserialized.getAttempts());
        assertEquals(300000L, deserialized.getTimeout());
    }
}
