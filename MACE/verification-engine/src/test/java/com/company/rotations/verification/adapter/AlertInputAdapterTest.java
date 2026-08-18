package com.company.rotations.verification.adapter;

import com.company.rotations.verification.model.CredentialAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertInputAdapter Tests")
class AlertInputAdapterTest {

    private AlertInputAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AlertInputAdapter();
    }

    @Nested
    @DisplayName("Basic field extraction")
    class BasicExtractionTests {

        @Test
        @DisplayName("Should convert basic alert payload to CredentialAlert")
        void basicConversion() {
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-123",
                    "source", "gitguardian",
                    "account_hint", "client-account-456",
                    "credential_value", "AKIAIOSFODNN7EXAMPLE:secretkey123",
                    "credential_value_hash", "sha256abc123",
                    "provider", "aws",
                    "received_at", "2024-01-15T10:30:00Z"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert);
            assertEquals("evt-123", alert.getEventId());
            assertEquals("gitguardian", alert.getSource());
            assertEquals("client-account-456", alert.getAccountHint());
            assertEquals("AKIAIOSFODNN7EXAMPLE:secretkey123", alert.getCredentialValue());
            assertEquals("sha256abc123", alert.getCredentialValueHash());
            assertEquals("aws", alert.getProviderName());
            assertNotNull(alert.getReceivedAt());
        }

        @Test
        @DisplayName("Should extract credential_value from second key fallback")
        void extractFromSecondKey() {
            Map<String, Object> payload = Map.of(
                    "secret_value", "AKIAIOSFODNN7EXAMPLE:secretkey123"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert);
            assertEquals("AKIAIOSFODNN7EXAMPLE:secretkey123", alert.getCredentialValue());
        }

        @Test
        @DisplayName("Should extract credential_value from third key fallback")
        void extractFromThirdKey() {
            Map<String, Object> payload = Map.of(
                    "access_key", "AKIAIOSFODNN7EXAMPLE:secretkey123"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert);
            assertEquals("AKIAIOSFODNN7EXAMPLE:secretkey123", alert.getCredentialValue());
        }

        @Test
        @DisplayName("Should prefer first key over fallbacks")
        void preferFirstKey() {
            Map<String, Object> payload = Map.of(
                    "credential_value", "AKIAIOSFODNN7EXAMPLE:first",
                    "secret_value", "AKIAIOSFODNN7EXAMPLE:second"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("AKIAIOSFODNN7EXAMPLE:first", alert.getCredentialValue());
        }

        @Test
        @DisplayName("Should extract provider from second key fallback")
        void providerFromSecondKey() {
            Map<String, Object> payload = Map.of(
                    "provider_name", "azure"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("azure", alert.getProviderName());
        }

        @Test
        @DisplayName("Should extract event_id from second key fallback")
        void eventIdFromSecondKey() {
            Map<String, Object> payload = Map.of(
                    "id", "evt-456"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("evt-456", alert.getEventId());
        }

        @Test
        @DisplayName("Should extract event_id from third key fallback")
        void eventIdFromThirdKey() {
            Map<String, Object> payload = Map.of(
                    "eventId", "evt-789"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("evt-789", alert.getEventId());
        }
    }

    @Nested
    @DisplayName("Context extraction")
    class ContextExtractionTests {

        @Test
        @DisplayName("Should extract context fields from first key")
        void extractContextFields() {
            Map<String, Object> context = Map.of(
                    "repository", "my-repo",
                    "file", "src/credentials.py",
                    "commit", "abc123def",
                    "visibility", "public"
            );
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-100",
                    "context", context
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert.getContext());
            assertEquals("my-repo", alert.getContext().getRepository());
            assertEquals("src/credentials.py", alert.getContext().getFile());
            assertEquals("abc123def", alert.getContext().getCommit());
            assertEquals("public", alert.getContext().getVisibility());
        }

        @Test
        @DisplayName("Should extract context fields from fallback keys")
        void extractContextFallbackKeys() {
            Map<String, Object> context = Map.of(
                    "repo", "another-repo",
                    "git_file", "lib/auth.ts",
                    "git_commit", "def456ghi",
                    "commit_visibility", "private"
            );
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-200",
                    "context", context
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert.getContext());
            assertEquals("another-repo", alert.getContext().getRepository());
            assertEquals("lib/auth.ts", alert.getContext().getFile());
            assertEquals("def456ghi", alert.getContext().getCommit());
            assertEquals("private", alert.getContext().getVisibility());
        }

        @Test
        @DisplayName("Should return null context when context key missing")
        void nullContextWhenMissing() {
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-300"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNull(alert.getContext());
        }

        @Test
        @DisplayName("Should return null context when context is not a map")
        void nullContextWhenNotMap() {
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-300",
                    "context", "not-a-map"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNull(alert.getContext());
        }
    }

    @Nested
    @DisplayName("Edge cases and null handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return valid alert for null payload")
        void nullPayload() {
            CredentialAlert alert = adapter.toCredentialAlert(null);

            assertNotNull(alert);
            assertNull(alert.getEventId());
            assertNull(alert.getSource());
            assertNull(alert.getAccountHint());
            assertNull(alert.getCredentialValue());
            assertNull(alert.getContext());
        }

        @Test
        @DisplayName("Should return valid alert for empty payload")
        void emptyPayload() {
            CredentialAlert alert = adapter.toCredentialAlert(Map.of());

            assertNotNull(alert);
            assertNull(alert.getEventId());
        }

        @Test
        @DisplayName("Should handle instant with camelCase key")
        void instantCamelCaseKey() {
            Map<String, Object> payload = Map.of(
                    "receivedAt", Instant.parse("2024-06-15T12:00:00Z")
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert.getReceivedAt());
            assertEquals(Instant.parse("2024-06-15T12:00:00Z"), alert.getReceivedAt());
        }

        @Test
        @DisplayName("Should handle invalid instant gracefully")
        void invalidInstant() {
            Map<String, Object> payload = Map.of(
                    "received_at", "not-a-valid-instant"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNull(alert.getReceivedAt());
        }

        @Test
        @DisplayName("Should handle non-string values in payload")
        void nonStringValue() {
            Map<String, Object> payload = Map.of(
                    "event_id", 12345
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("12345", alert.getEventId());
        }

        @Test
        @DisplayName("Should capture raw payload as copy")
        void rawPayloadCopied() {
            Map<String, Object> payload = Map.of(
                    "event_id", "evt-123",
                    "credential_value", "AKIAIOSFODNN7EXAMPLE:secret"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertNotNull(alert.getRawPayload());
            assertEquals(2, alert.getRawPayload().size());
            assertEquals("evt-123", alert.getRawPayload().get("event_id"));
        }
    }

    @Nested
    @DisplayName("Key fallback chain")
    class FallbackChainTests {

        @Test
        @DisplayName("Should try all fallback keys for source")
        void sourceFallbackChain() {
            Map<String, Object> payload = Map.of(
                    "provider_name", "gcp"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("gcp", alert.getSource());
        }

        @Test
        @DisplayName("Should try all fallback keys for account_hint")
        void accountHintFallbackChain() {
            Map<String, Object> payload = Map.of(
                    "accountHint", "hint-account-789"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("hint-account-789", alert.getAccountHint());
        }

        @Test
        @DisplayName("Should try all fallback keys for credential_value_hash")
        void hashFallbackChain() {
            Map<String, Object> payload = Map.of(
                    "value_hash", "sha256hash456"
            );

            CredentialAlert alert = adapter.toCredentialAlert(payload);

            assertEquals("sha256hash456", alert.getCredentialValueHash());
        }
    }
}
