package com.company.rotations.alerting.adapter;

import com.company.rotations.models.GenericAlertModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.company.rotations.spi.AlertAdapter;

@Component
public class GitGuardianAdapter implements AlertAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GitGuardianAdapter.class);

    @Override
    public String getProviderName() {
        return "gitguardian";
    }

    @Override
    public GenericAlertModel toGenericAlert(Map<String, Object> rawPayload) {
        logger.debug("Mapping GitGuardian API v2 payload to GenericAlertModel");

        Map<String, Object> incident = extractMap(rawPayload, "incident");
        if (incident == null) {
            incident = new LinkedHashMap<>();
        }

        GenericAlertModel model = new GenericAlertModel();
        model.setSource("gitguardian");

        String id = extractString(incident, "id");
        model.setSourceEventId(id != null ? id : extractString(rawPayload, "event_id", "id"));
        model.setEventId(model.getSourceEventId());
        model.setReceivedAt(Instant.now());

        GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
        secret.setType(detectSecretType(incident));
        secret.setValueHash(extractString(incident, "value_hash", "secret_hash", "hash"));
        secret.setPattern(extractString(incident, "secret_type", "detector", "type"));
        model.setDetectedSecret(secret);

        GenericAlertModel.AlertContext ctx = new GenericAlertModel.AlertContext();
        ctx.setRepository(extractString(incident, "repository", "repo_url", "git_repo"));
        ctx.setFile(extractString(incident, "file", "git_file", "path"));
        ctx.setCommit(extractString(incident, "commit", "git_commit", "revision"));
        ctx.setLine(extractInteger(incident, "line", "git_line"));
        ctx.setVisibility(detectVisibility(incident));
        ctx.setFoundAt(extractInstant(incident, "created_at", "date", "detected_at"));
        model.setContext(ctx);

        model.setProviderSeverity(extractString(incident, "severity", "priority"));

        GenericAlertModel.DetectorState state = new GenericAlertModel.DetectorState();
        state.setIsNew(isNewIncident(incident));
        state.setPreviouslyFlagged(hasBeenFlagged(incident));
        state.setFlagCount(countFlags(incident));
        model.setDetectorState(state);

        model.setRawPayload(Collections.unmodifiableMap(new LinkedHashMap<>(rawPayload)));

        logger.debug("Mapped GitGuardian alert: eventId={}, sourceEventId={}, secretType={}",
                model.getEventId(), model.getSourceEventId(), secret.getType());
        return model;
    }

    private String detectSecretType(Map<String, Object> incident) {
        String secretType = extractString(incident, "secret_type", "detector");
        if (secretType != null && !secretType.isBlank()) {
            return secretType.toLowerCase();
        }

        String rawPayload = extractString(incident, "trigger", "diff");
        if (rawPayload != null) {
            if (rawPayload.contains("AKIA") || rawPayload.contains("aws")) {
                return "aws_access_key";
            }
            if (rawPayload.contains("eyJ") || rawPayload.contains("token")) {
                return "jwt";
            }
            if (rawPayload.contains("AIzaSy") || rawPayload.contains("google")) {
                return "google_api_key";
            }
        }

        return "generic";
    }

    private String detectVisibility(Map<String, Object> incident) {
        String visibility = extractString(incident, "visibility", "commit_visibility", "repo_visibility");
        if (visibility != null) {
            String lower = visibility.toLowerCase();
            if (lower.contains("public") || lower.contains("public")) {
                return "public";
            }
            if (lower.contains("private") || lower.contains("internal")) {
                return "private";
            }
        }
        return "unknown";
    }

    private boolean isNewIncident(Map<String, Object> incident) {
        String isNew = extractString(incident, "is_new", "new_commit", "is_new_incident");
        if (isNew != null) {
            return "true".equalsIgnoreCase(isNew) || "1".equals(isNew) || Boolean.TRUE.toString().equals(isNew);
        }
        return true;
    }

    private boolean hasBeenFlagged(Map<String, Object> incident) {
        String flagged = extractString(incident, "previously_flagged", "is_duplication", "is_repeat");
        if (flagged != null) {
            return "true".equalsIgnoreCase(flagged) || "1".equals(flagged);
        }
        return false;
    }

    private int countFlags(Map<String, Object> incident) {
        Object flagCount = incident.get("flag_count");
        if (flagCount instanceof Number) {
            return ((Number) flagCount).intValue();
        }
        String countStr = extractString(incident, "flag_count", "times_found", "occurrences");
        if (countStr != null) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String extractString(Map<String, Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private Integer extractInteger(Map<String, Object> payload, String... keys) {
        if (payload == null) return null;
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
        if (payload == null) return null;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
