package com.company.rotations.alerting.model;

import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookPayloadTest {

    @Test
    @DisplayName("Should construct with all fields")
    void shouldConstructWithAllFields() {
        GenericAlertModel alert = new GenericAlertModel();
        alert.setEventId("event-123");
        alert.setSource("gitguardian");

        Instant now = Instant.now();
        WebhookPayload payload = new WebhookPayload(alert, "raw-body-string", "gitguardian", now);

        assertEquals(alert, payload.alert());
        assertEquals("raw-body-string", payload.rawBody());
        assertEquals("gitguardian", payload.source());
        assertEquals(now, payload.receivedAt());
    }

    @Test
    @DisplayName("Should handle null alert")
    void shouldHandleNullAlert() {
        WebhookPayload payload = new WebhookPayload(null, "body", "src", Instant.now());
        assertNull(payload.alert());
        assertEquals("body", payload.rawBody());
    }

    @Test
    @DisplayName("Should handle null rawBody")
    void shouldHandleNullRawBody() {
        GenericAlertModel alert = new GenericAlertModel();
        WebhookPayload payload = new WebhookPayload(alert, null, "src", Instant.now());
        assertNull(payload.rawBody());
    }

    @Test
    @DisplayName("Should be immutable record")
    void shouldBeImmutableRecord() {
        GenericAlertModel alert = new GenericAlertModel();
        alert.setEventId("test");
        Instant now = Instant.now();

        WebhookPayload payload = new WebhookPayload(alert, "body", "src", now);

        assertInstanceOf(WebhookPayload.class, payload);
        assertEquals(4, WebhookPayload.class.getRecordComponents().length);
    }
}
