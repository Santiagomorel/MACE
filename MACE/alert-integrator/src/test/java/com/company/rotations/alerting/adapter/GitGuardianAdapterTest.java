package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GitGuardianAdapterTest {

    private Map<String, Object> buildPayload(Map<String, Object> incident, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "gitguardian");
        payload.put("incident", incident != null ? incident : new LinkedHashMap<>());
        if (extra != null) {
            payload.putAll(extra);
        }
        return payload;    }

    @Nested
    @DisplayName("Basic Mapping")
    class BasicMappingTests {

        @Test
        @DisplayName("Should map basic payload fields")
        void shouldMapBasicFields() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-event-123");
            incident.put("value_hash", "hash123");
            incident.put("secret_type", "AWS_KEY");
            incident.put("repository", "my-repo");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertEquals("gitguardian", result.getSource());
            assertEquals("gg-event-123", result.getSourceEventId());
            assertEquals("gg-event-123", result.getEventId());
            assertNotNull(result.getReceivedAt());
            assertEquals("hash123", result.getDetectedSecret().getValueHash());
            assertEquals("aws_key", result.getDetectedSecret().getType());
            assertEquals("my-repo", result.getContext().getRepository());
        }

        @Test
        @DisplayName("Should return generic type when secret type is blank")
        void shouldReturnGenericWhenSecretTypeBlank() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-456");
            incident.put("secret_type", "");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertEquals("generic", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should handle null incident in payload")
        void shouldHandleNullIncident() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(payload);

            assertEquals("gitguardian", result.getSource());
            assertNull(result.getContext().getRepository());
            assertEquals("generic", result.getDetectedSecret().getType());
        }
    }

    @Nested
    @DisplayName("Secret Type Detection")
    class SecretTypeTests {

        @Test
        @DisplayName("Should detect AWS key from trigger text")
        void shouldDetectAwsKey() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-aws");
            incident.put("trigger", "AKIAIOSFODNN7EXAMPLE");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("aws_access_key", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should detect JWT from trigger text")
        void shouldDetectJwt() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-jwt");
            incident.put("trigger", "eyJhbGciOiJIUzI1NiJ9");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("jwt", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should detect Google API key from trigger text")
        void shouldDetectGoogleApiKey() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-google");
            incident.put("trigger", "AIzaSyDxxxxxxxxxx");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("google_api_key", result.getDetectedSecret().getType());
        }

        @Test
        @DisplayName("Should detect secret type from incident fields with multiple key fallbacks")
        void shouldDetectFromMultipleKeys() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-456");
            incident.put("detector", "Generic_API_Key");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("generic_api_key", result.getDetectedSecret().getType());
        }
    }

    @Nested
    @DisplayName("Context Mapping")
    class ContextMappingTests {

        @Test
        @DisplayName("Should map context fields with multiple key fallbacks")
        void shouldMapContextFields() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-c");
            incident.put("repository", "my-repo");
            incident.put("file", "src/config.py");
            incident.put("commit", "abc123def");
            incident.put("line", 42);
            incident.put("visibility", "public");
            incident.put("created_at", Instant.parse("2024-01-15T10:30:00Z"));

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertEquals("my-repo", result.getContext().getRepository());
            assertEquals("src/config.py", result.getContext().getFile());
            assertEquals("abc123def", result.getContext().getCommit());
            assertEquals(42, result.getContext().getLine());
            assertEquals("public", result.getContext().getVisibility());
            assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result.getContext().getFoundAt());
        }

        @Test
        @DisplayName("Should map alternate context fields")
        void shouldMapAlternateContextKeys() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-alt");
            incident.put("repo_url", "https://github.com/org/repo");
            incident.put("git_file", "path/to/file");
            incident.put("git_line", 100);
            incident.put("commit_visibility", "private");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertEquals("https://github.com/org/repo", result.getContext().getRepository());
            assertEquals("path/to/file", result.getContext().getFile());
            assertEquals(100, result.getContext().getLine());
            assertEquals("private", result.getContext().getVisibility());
        }
    }

    @Nested
    @DisplayName("Detector State")
    class DetectorStateTests {

        @Test
        @DisplayName("Should detect new incident")
        void shouldDetectNewIncident() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-new");
            incident.put("is_new", "true");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertTrue(result.getDetectorState().isNew());
        }

        @Test
        @DisplayName("Should detect previously flagged")
        void shouldDetectPreviouslyFlagged() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-flagged");
            incident.put("previously_flagged", "true");
            incident.put("flag_count", 3);

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertTrue(result.getDetectorState().isPreviouslyFlagged());
            assertEquals(3, result.getDetectorState().getFlagCount());
        }

        @Test
        @DisplayName("Should default to new=true and not flagged")
        void shouldDefaultToNewAndNotFlagged() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-default");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertTrue(result.getDetectorState().isNew());
            assertFalse(result.getDetectorState().isPreviouslyFlagged());
            assertEquals(0, result.getDetectorState().getFlagCount());
        }
    }

    @Nested
    @DisplayName("Provider Severity")
    class SeverityTests {

        @Test
        @DisplayName("Should map provider severity")
        void shouldMapSeverity() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-sev");
            incident.put("severity", "high");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));

            assertEquals("high", result.getProviderSeverity());
        }

        @Test
        @DisplayName("Should store raw payload as unmodifiable map")
        void shouldStoreRawPayloadUnmodifiable() {
            Map<String, Object> payload = buildPayload(new LinkedHashMap<>(), null);
            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(payload);

            assertNotNull(result.getRawPayload());
            assertThrows(UnsupportedOperationException.class, () -> result.getRawPayload().put("test", "value"));
        }
    }

    @Nested
    @DisplayName("Provider Name")
    class ProviderNameTests {

        @Test
        @DisplayName("Should return gitguardian provider name")
        void shouldReturnProviderName() {
            assertEquals("gitguardian", new GitGuardianAdapter().getProviderName());
        }
    }

    @Nested
    @DisplayName("Visibility Detection")
    class VisibilityTests {

        @Test
        @DisplayName("Should detect public visibility")
        void shouldDetectPublicVisibility() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-vis");
            incident.put("visibility", "public");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("public", result.getContext().getVisibility());
        }

        @Test
        @DisplayName("Should detect private visibility")
        void shouldDetectPrivateVisibility() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-vis");
            incident.put("visibility", "private");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("private", result.getContext().getVisibility());
        }

        @Test
        @DisplayName("Should default to unknown visibility")
        void shouldDefaultUnknownVisibility() {
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "gg-vis");

            GenericAlertModel result = new GitGuardianAdapter().toGenericAlert(buildPayload(incident, null));
            assertEquals("unknown", result.getContext().getVisibility());
        }
    }
}
