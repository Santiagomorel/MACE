package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AlertTest {

    private final ObjectMapper mapper = new ObjectMapper();

    AlertTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateAlertWithAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Alert alert = new Alert(id, "gitguardian", AlertType.AWS_ACCESS_KEY, "tenant-1",
                AlertStatus.PENDING, "{\"test\": true}", "{\"raw\": true}", now, AlertType.AWS_ACCESS_KEY);

        assertEquals(id, alert.getId());
        assertEquals("gitguardian", alert.getProviderName());
        assertEquals(AlertType.AWS_ACCESS_KEY, alert.getCredentialType());
        assertEquals("tenant-1", alert.getTenantId());
        assertEquals(AlertStatus.PENDING, alert.getStatus());
        assertNotNull(alert.getReceivedAt());
    }

    @Test
    void shouldDefaultStatusToPending() {
        Alert alert = new Alert();
        assertEquals(AlertStatus.PENDING, alert.getStatus());
    }

    @Test
    void shouldDefaultReceivedAtToNow() {
        Alert alert = new Alert();
        assertNotNull(alert.getReceivedAt());
    }

    @Test
    void shouldSerializeAndDeserializeAlert() throws Exception {
        Instant now = Instant.now();
        Alert alert = new Alert(UUID.randomUUID(), "test-provider", AlertType.IAM_USER,
                "tenant-2", AlertStatus.PROCESSING, "{\"key\":\"value\"}", "{\"raw\":true}",
                now, AlertType.IAM_USER);

        String json = mapper.writeValueAsString(alert);
        Alert deserialized = mapper.readValue(json, Alert.class);

        assertEquals(alert.getId(), deserialized.getId());
        assertEquals(alert.getProviderName(), deserialized.getProviderName());
        assertEquals(alert.getCredentialType(), deserialized.getCredentialType());
        assertEquals(alert.getTenantId(), deserialized.getTenantId());
        assertEquals(alert.getStatus(), deserialized.getStatus());
    }

    @Test
    void shouldToStringNotExposeSensitiveData() {
        Alert alert = new Alert();
        alert.setProviderName("gitguardian");
        alert.setCredentialType(AlertType.AWS_ACCESS_KEY);
        alert.setTenantId("tenant-1");
        alert.setStatus(AlertStatus.PENDING);

        String str = alert.toString();
        assertFalse(str.contains("rawPayload"));
        assertTrue(str.contains("Alert{id="));
        assertTrue(str.contains("providerName='gitguardian'"));
    }
}
