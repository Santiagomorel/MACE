package com.company.rotations.verification.adapter;

import com.company.rotations.verification.model.CredentialAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class AlertInputAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AlertInputAdapter.class);

    public CredentialAlert toCredentialAlert(Map<String, Object> rawPayload) {
        logger.info("Converting raw alert payload to CredentialAlert");

        CredentialAlert alert = new CredentialAlert();

        alert.setEventId(extractString(rawPayload, "event_id", "id", "eventId"));
        alert.setSource(extractString(rawPayload, "source", "provider_name"));
        alert.setAccountHint(extractString(rawPayload, "account_hint", "accountHint"));
        alert.setCredentialValue(extractString(rawPayload, "credential_value", "secret_value", "access_key"));
        alert.setCredentialValueHash(extractString(rawPayload, "credential_value_hash", "value_hash", "hash"));
        alert.setProviderName(extractString(rawPayload, "provider", "provider_name"));
        alert.setReceivedAt(extractInstant(rawPayload, "received_at", "receivedAt", "timestamp"));

        Map<String, Object> context = extractMap(rawPayload, "context");
        if (context != null) {
            CredentialAlert.AlertContext alertContext = new CredentialAlert.AlertContext();
            alertContext.setRepository(extractString(context, "repository", "repo", "git_repo"));
            alertContext.setFile(extractString(context, "file", "git_file", "path"));
            alertContext.setCommit(extractString(context, "commit", "git_commit", "revision"));
            alertContext.setVisibility(extractString(context, "visibility", "commit_visibility"));
            alert.setContext(alertContext);
        }

        alert.setRawPayload(Map.copyOf(rawPayload));

        logger.debug("Converted alert: eventId={}, source={}, accountHint={}",
                alert.getEventId(), alert.getSource(), alert.getAccountHint());

        return alert;
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
