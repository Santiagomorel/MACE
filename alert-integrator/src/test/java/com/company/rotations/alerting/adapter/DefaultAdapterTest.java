package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAdapterTest {

    @Nested
    @DisplayName("Basic Mapping")
    class BasicMappingTests {

        @Test
        @DisplayName("Should map payload to generic model")
        void shouldMapPayload() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_id", "default-event-1");
            payload.put("value_hash", "h123");
            payload.put("pattern", "api_key");
            payload.put("repository", "test-repo");
            payload.put("file", "config.yml");

            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);

            assertEquals("unknown", result.getSource());
            assertEquals("default-event-1", result.getSourceEventId());
            assertNotNull(result.getEventId());
            assertNotNull(result.getReceivedAt());
            assertEquals("h123", result.getDetectedSecret().getValueHash());
            assertEquals("api_key", result.getDetectedSecret().getPattern());
            assertEquals("test-repo", result.getContext().getRepository());
            assertEquals("config.yml", result.getContext().getFile());
            assertEquals("generic", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should handle empty payload")
        void shouldHandleEmptyPayload() {
            Map<String, Object> payload = new HashMap<>();
            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);

            assertEquals("unknown", result.getSource());
            assertNull(result.getSourceEventId());
            assertNotNull(result.getEventId());
            assertEquals("generic", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should map alternate keys")
        void shouldMapAlternateKeys() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("incident_id", "alt-id");
            payload.put("secret_hash", "hash-alt");
            payload.put("secret_type", "jwt");
            payload.put("repo", "alt-repo");
            payload.put("file_path", "alt/path.js");
            payload.put("line_number", 55);
            payload.put("visibility", "internal");
            payload.put("found_at", "2024-03-01T12:00:00Z");

            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);

            assertEquals("alt-id", result.getSourceEventId());
            assertEquals("hash-alt", result.getDetectedSecret().getValueHash());
            assertEquals("jwt", result.getDetectedSecret().getPattern());
            assertEquals("alt-repo", result.getContext().getRepository());
            assertEquals("alt/path.js", result.getContext().getFile());
            assertEquals(55, result.getContext().getLine());
            assertEquals("internal", result.getContext().getVisibility());
            assertEquals(Instant.parse("2024-03-01T12:00:00Z"), result.getContext().getFoundAt());
        }

        @Test
        @DisplayName("Should handle numeric line values")
        void shouldHandleNumericLine() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("line", 77);
            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);
            assertEquals(77, result.getContext().getLine());
        }

        @Test
        @DisplayName("Should handle string line values")
        void shouldHandleStringLine() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("line", "42");
            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);
            assertEquals(42, result.getContext().getLine());
        }
    }

    @Nested
    @DisplayName("Provider Name")
    class ProviderNameTests {

        @Test
        @DisplayName("Should return default provider name")
        void shouldReturnProviderName() {
            assertEquals("default", new DefaultAdapter().getProviderName());
        }
    }

    @Nested
    @DisplayName("Raw Payload")
    class RawPayloadTests {

        @Test
        @DisplayName("Should store raw payload")
        void shouldStoreRawPayload() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("raw_key", "raw_value");
            GenericAlertModel result = new DefaultAdapter().toGenericAlert(payload);

            assertNotNull(result.getRawPayload());
            assertEquals("raw_value", result.getRawPayload().get("raw_key"));
        }
    }
}
