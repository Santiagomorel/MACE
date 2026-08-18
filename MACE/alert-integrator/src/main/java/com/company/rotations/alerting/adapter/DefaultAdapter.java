package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

import com.company.rotations.spi.AlertAdapter;

@Component
public class DefaultAdapter implements AlertAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAdapter.class);

    @Override
    public String getProviderName() {
        return "default";
    }

    @Override
    public GenericAlertModel toGenericAlert(Map<String, Object> rawPayload) {
        logger.debug("Applying default adapter to raw payload");
        GenericAlertModel model = new GenericAlertModel();
        model.setSource("unknown");
        model.setSourceEventId(extractString(rawPayload, "event_id", "incident_id", "id"));
        model.setEventId(generateEventId());
        model.setReceivedAt(Instant.now());

        GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
        secret.setType("generic");
        secret.setValueHash(extractString(rawPayload, "value_hash", "secret_hash", "hash"));
        secret.setPattern(extractString(rawPayload, "pattern", "secret_type", "type"));
        model.setDetectedSecret(secret);

        GenericAlertModel.AlertContext ctx = new GenericAlertModel.AlertContext();
        ctx.setRepository(extractString(rawPayload, "repository", "repo", "source_repo"));
        ctx.setFile(extractString(rawPayload, "file", "filepath", "file_path"));
        ctx.setCommit(extractString(rawPayload, "commit", "revision", "sha"));
        ctx.setLine(extractInteger(rawPayload, "line", "line_number"));
        ctx.setVisibility(extractString(rawPayload, "visibility", "secret_visibility"));
        ctx.setFoundAt(extractInstant(rawPayload, "found_at", "detected_at"));
        model.setContext(ctx);

        model.setProviderSeverity(extractString(rawPayload, "severity", "provider_severity"));
        model.setRawPayload(rawPayload);

        return model;
    }

    private String extractString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private Integer extractInteger(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                try {
                    return Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    // continue to next key
                }
            }
        }
        return null;
    }

    private Instant extractInstant(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                try {
                    if (value instanceof Instant) {
                        return (Instant) value;
                    }
                    return Instant.parse(value.toString());
                } catch (Exception e) {
                    // continue to next key
                }
            }
        }
        return null;
    }

    private String generateEventId() {
        return java.util.UUID.randomUUID().toString();
    }
}
