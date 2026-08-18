package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import com.company.rotations.spi.AlertAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdapterRegistryTest {

    private Map<String, Object> createPayload(String source, String eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("incident", Map.of(
                "id", eventId,
                "value_hash", "abc123",
                "secret_type", "aws_key",
                "repository", "my-repo"
        ));
        return payload;
    }

    @Nested
    @DisplayName("Adapter Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should register all provided adapters")
        void shouldRegisterAllAdapters() {
            AlertAdapter mockAdapter1 = new AlertAdapter() {
                @Override public String getProviderName() { return "provider-a"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            AlertAdapter mockAdapter2 = new AlertAdapter() {
                @Override public String getProviderName() { return "provider-b"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            DefaultAdapter defaultAdapter = new DefaultAdapter();

            AdapterRegistry registry = new AdapterRegistry(List.of(mockAdapter1, mockAdapter2), defaultAdapter);

            assertEquals(2, registry.getRegisteredAdapterCount());
            assertTrue(registry.getRegisteredProviders().contains("provider-a"));
            assertTrue(registry.getRegisteredProviders().contains("provider-b"));
        }

        @Test
        @DisplayName("Should skip duplicate adapter registrations")
        void shouldSkipDuplicateRegistrations() {
            AlertAdapter adapter1 = new AlertAdapter() {
                @Override public String getProviderName() { return "duplicated"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            AlertAdapter adapter2 = new AlertAdapter() {
                @Override public String getProviderName() { return "duplicated"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            DefaultAdapter defaultAdapter = new DefaultAdapter();

            AdapterRegistry registry = new AdapterRegistry(List.of(adapter1, adapter2), defaultAdapter);

            assertEquals(1, registry.getRegisteredAdapterCount());
        }

        @Test
        @DisplayName("Should work with no custom adapters")
        void shouldWorkWithNoCustomAdapters() {
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(), defaultAdapter);

            assertEquals(0, registry.getRegisteredAdapterCount());
        }
    }

    @Nested
    @DisplayName("Adapter Resolution")
    class ResolutionTests {

        @Test
        @DisplayName("Should return present adapter for known source")
        void shouldReturnKnownAdapter() {
            AlertAdapter gitGuardian = new GitGuardianAdapter();
            DefaultAdapter defaultAdapter = new DefaultAdapter();

            AdapterRegistry registry = new AdapterRegistry(List.of(gitGuardian), defaultAdapter);

            Optional<AlertAdapter> adapter = registry.getAdapter("gitguardian");
            assertTrue(adapter.isPresent());
            assertSame(gitGuardian, adapter.get());
        }

        @Test
        @DisplayName("Should return empty for unknown source")
        void shouldReturnEmptyForUnknown() {
            AlertAdapter gitGuardian = new GitGuardianAdapter();
            DefaultAdapter defaultAdapter = new DefaultAdapter();

            AdapterRegistry registry = new AdapterRegistry(List.of(gitGuardian), defaultAdapter);

            Optional<AlertAdapter> adapter = registry.getAdapter("unknown-source");
            assertTrue(adapter.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for null source")
        void shouldReturnEmptyForNull() {
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(), defaultAdapter);

            Optional<AlertAdapter> adapter = registry.getAdapter(null);
            assertTrue(adapter.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for blank source")
        void shouldReturnEmptyForBlank() {
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(), defaultAdapter);

            Optional<AlertAdapter> adapter = registry.getAdapter("   ");
            assertTrue(adapter.isEmpty());
        }

        @Test
        @DisplayName("Should return default adapter when no specific adapter found")
        void shouldReturnDefaultAdapter() {
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(), defaultAdapter);

            AlertAdapter resolved = registry.resolveAdapter("unknown-source");
            assertSame(defaultAdapter, resolved);
        }

        @Test
        @DisplayName("Should return specific adapter over default")
        void shouldReturnSpecificOverDefault() {
            AlertAdapter gitGuardian = new GitGuardianAdapter();
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(gitGuardian), defaultAdapter);

            AlertAdapter resolved = registry.resolveAdapter("gitguardian");
            assertSame(gitGuardian, resolved);
        }
    }

    @Nested
    @DisplayName("Adapt")
    class AdaptTests {

        @Test
        @DisplayName("Should adapt using resolved adapter")
        void shouldAdaptUsingResolved() {
            AlertAdapter customAdapter = new AlertAdapter() {
                @Override public String getProviderName() { return "custom"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> payload) {
                    GenericAlertModel model = new GenericAlertModel();
                    model.setSource("custom");
                    model.setEventId("adapted-id");
                    return model;
                }
            };
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(customAdapter), defaultAdapter);

            Map<String, Object> payload = new HashMap<>();
            payload.put("source", "custom");

            GenericAlertModel result = registry.adapt("custom", payload);
            assertEquals("custom", result.getSource());
            assertEquals("adapted-id", result.getEventId());
        }

        @Test
        @DisplayName("Should adapt using default adapter")
        void shouldAdaptUsingDefault() {
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(), defaultAdapter);

            Map<String, Object> payload = new HashMap<>();
            payload.put("event_id", "test-event-123");

            GenericAlertModel result = registry.adapt("unknown-source", payload);
            assertEquals("test-event-123", result.getSourceEventId());
        }

        @Test
        @DisplayName("Should return list of registered providers")
        void shouldReturnProvidersList() {
            AlertAdapter adapterA = new AlertAdapter() {
                @Override public String getProviderName() { return "alpha"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            AlertAdapter adapterB = new AlertAdapter() {
                @Override public String getProviderName() { return "beta"; }
                @Override public GenericAlertModel toGenericAlert(Map<String, Object> p) { return new GenericAlertModel(); }
            };
            DefaultAdapter defaultAdapter = new DefaultAdapter();
            AdapterRegistry registry = new AdapterRegistry(List.of(adapterA, adapterB), defaultAdapter);

            List<String> providers = registry.getRegisteredProviders();
            assertEquals(2, providers.size());
            assertTrue(providers.contains("alpha"));
            assertTrue(providers.contains("beta"));
        }
    }
}
