package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import com.company.rotations.spi.AlertAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdapterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AdapterRegistry.class);

    private final Map<String, AlertAdapter> adapters = new ConcurrentHashMap<>();
    private final AlertAdapter defaultAdapter;

    public AdapterRegistry(List<AlertAdapter> adapterList, DefaultAdapter defaultAdapter) {
        this.defaultAdapter = defaultAdapter;
        for (AlertAdapter adapter : adapterList) {
            String providerName = adapter.getProviderName();
            if (adapters.putIfAbsent(providerName, adapter) != null) {
                logger.warn("Duplicate adapter registration for provider '{}', skipping", providerName);
            } else {
                logger.info("Registered adapter for provider '{}'", providerName);
            }
        }
    }

    public Optional<AlertAdapter> getAdapter(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(source));
    }

    public AlertAdapter resolveAdapter(String source) {
        Optional<AlertAdapter> adapter = getAdapter(source);
        if (adapter.isPresent()) {
            return adapter.get();
        }
        logger.info("No specific adapter found for source '{}', using default adapter", source);
        return defaultAdapter;
    }

    public GenericAlertModel adapt(String source, Map<String, Object> rawPayload) {
        AlertAdapter adapter = resolveAdapter(source);
        return adapter.toGenericAlert(rawPayload);
    }

    public int getRegisteredAdapterCount() {
        return adapters.size();
    }

    public List<String> getRegisteredProviders() {
        return List.copyOf(adapters.keySet());
    }

    public String getProviderName(String source) {
        Optional<AlertAdapter> adapter = getAdapter(source);
        if (adapter.isPresent()) {
            return adapter.get().getProviderName();
        }
        return defaultAdapter.getProviderName();
    }
}
