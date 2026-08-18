package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    AuditEventTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateAuditEvent() {
        UUID alertId = UUID.randomUUID();

        AuditEvent event = new AuditEvent(
                AuditEventType.ALERT_INGESTED, "tenant-1", alertId,
                "{\"source\":\"gitguardian\"}", "user-123");

        assertEquals(AuditEventType.ALERT_INGESTED, event.getEventType());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals(alertId, event.getAlertId());
        assertEquals("{\"source\":\"gitguardian\"}", event.getDetails());
        assertEquals("user-123", event.getUserId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        UUID alertId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(
                AuditEventType.ROTATION_COMPLETED, "tenant-2", alertId,
                "{\"rotationId\":\"rot-123\"}", "admin");

        String json = mapper.writeValueAsString(event);
        AuditEvent deserialized = mapper.readValue(json, AuditEvent.class);

        assertEquals(AuditEventType.ROTATION_COMPLETED, deserialized.getEventType());
        assertEquals("tenant-2", deserialized.getTenantId());
        assertEquals(alertId, deserialized.getAlertId());
        assertEquals("{\"rotationId\":\"rot-123\"}", deserialized.getDetails());
    }
}
