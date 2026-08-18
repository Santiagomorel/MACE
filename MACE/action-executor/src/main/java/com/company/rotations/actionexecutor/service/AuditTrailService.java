package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.models.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    public void logRotationTransition(RotationTransitionDto transition) {
        log.info("[ROTATION] Alert {} transitioned from {} to {} (attempt: {}, duration: {}ms)",
                transition.getAlertId(), transition.getFromState(), transition.getToState(),
                transition.getAttemptNumber(), transition.getDurationMs());

        if (transition.getErrorMessage() != null) {
            log.warn("[ROTATION] Error in transition: {}", transition.getErrorMessage());
        }
    }

    public void logRotationStarted(String tenantId, UUID alertId, String credentialId) {
        log.info("[ROTATION] Rotation started for tenant {} alert {} credential {}",
                tenantId, alertId, credentialId);
    }

    public void logRotationCompleted(String tenantId, UUID alertId, RotationResult result) {
        log.info("[ROTATION] Rotation completed for tenant {} alert {}: {} ({}ms, {} attempts)",
                tenantId, alertId, result.isSuccess(), result.getDurationMs(), result.getAttempts());
    }

    public void logRotationFailed(String tenantId, UUID alertId, String errorMessage) {
        log.warn("[ROTATION] Rotation failed for tenant {} alert {}: {}",
                tenantId, alertId, errorMessage);
    }

    public void logEscalation(String tenantId, UUID alertId, Object severity,
                              String reason, int attempts) {
        log.warn("[ROTATION] Escalation triggered for tenant {} alert {}: {} (after {} attempts)",
                tenantId, alertId, reason, attempts);
    }

    public void logTimeout(String tenantId, UUID alertId, Object severity) {
        log.error("[ROTATION] Timeout reached for tenant {} alert {}. Rotation cancelled.",
                tenantId, alertId);
    }

    public void logNotificationSent(String tenantId, UUID alertId, String channel,
                                    boolean success) {
        if (success) {
            log.info("[NOTIFICATION] Sent to {} for tenant {} alert {}",
                    channel, tenantId, alertId);
        } else {
            log.warn("[NOTIFICATION] Failed to send to {} for tenant {} alert {}",
                    channel, tenantId, alertId);
        }
    }

    public String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(formatValue(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        return value.toString().replace("\"", "\\\"");
    }
}
