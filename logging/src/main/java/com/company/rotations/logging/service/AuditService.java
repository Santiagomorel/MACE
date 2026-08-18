package com.company.rotations.logging.service;

import com.company.rotations.logging.model.AuditEvent;
import com.company.rotations.logging.repository.AuditEventRepository;
import com.company.rotations.models.GenericAlertModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Map;

public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean persistEnabled;

    public AuditService() {
        this.repository = null;
        this.persistEnabled = false;
        this.objectMapper = new ObjectMapper();
    }

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
        this.persistEnabled = true;
        this.objectMapper = new ObjectMapper();
    }

    public void logWebhookReceived(Map<String, Object> eventData) {
        logAudit(AuditEvent.AuditEventType.WEBHOOK_RECEIVED,
                 AuditEvent.AuditSeverity.INFO, "SUCCESS", null, eventData);
    }

    public void logVerificationStarted(Map<String, Object> eventData) {
        logAudit(AuditEvent.AuditEventType.VERIFICATION_STARTED,
                 AuditEvent.AuditSeverity.INFO, null, null, eventData);
    }

    public void logVerificationCompleted(Map<String, Object> eventData) {
        String status = (Boolean) eventData.get("success") ? "SUCCESS" : "FAILURE";
        logAudit(AuditEvent.AuditEventType.VERIFICATION_COMPLETED,
                 AuditEvent.AuditSeverity.INFO, status, null, eventData);
    }

    public void logRuleEvaluated(Map<String, Object> eventData) {
        logAudit(AuditEvent.AuditEventType.RULE_EVALUATED,
                 AuditEvent.AuditSeverity.AUDIT, null, null, eventData);
    }

    public void logActionExecuted(Map<String, Object> eventData) {
        String status = (Boolean) eventData.get("success") ? "SUCCESS" : "FAILURE";
        logAudit(AuditEvent.AuditEventType.ACTION_EXECUTED,
                 AuditEvent.AuditSeverity.AUDIT, status, null, eventData);
    }

    public void logDlqEnqueued(Map<String, Object> eventData) {
        logAudit(AuditEvent.AuditEventType.DLQ_ENQUEUED,
                 AuditEvent.AuditSeverity.ERROR, "FAILURE", null, eventData);
    }

    public void logDedupHit(Map<String, Object> eventData) {
        logAudit(AuditEvent.AuditEventType.DEDUP_HIT,
                 AuditEvent.AuditSeverity.WARN, null, null, eventData);
    }

    public void logWebhookReceived(String source, Map<String, Object> eventData) {
        Map<String, Object> enriched = new java.util.HashMap<>(eventData);
        enriched.put("source", source);
        logWebhookReceived(enriched);
    }

    public void logAlertIngested(String source, GenericAlertModel alert, String requestId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("event_id", alert.getEventId());
        data.put("source_event_id", alert.getSourceEventId());
        data.put("secret_type", alert.getDetectedSecret() != null ? alert.getDetectedSecret().getType() : "unknown");
        data.put("repository", alert.getContext() != null ? alert.getContext().getRepository() : null);
        logAudit(AuditEvent.AuditEventType.ALERT_INGESTED,
                 AuditEvent.AuditSeverity.INFO, "SUCCESS", "ingestion", data);
    }

    public void logAlertDeduplicated(String source, String sourceEventId, String reason) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("source_event_id", sourceEventId);
        data.put("dedup_reason", reason);
        logAudit(AuditEvent.AuditEventType.ALERT_DEDUPLICATED,
                 AuditEvent.AuditSeverity.WARN, null, "dedup", data);
    }

    public void logSignatureVerificationFailed(String source, String signature) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("signature_present", signature != null && !signature.isBlank());
        logAudit(AuditEvent.AuditEventType.SIGNATURE_VERIFICATION_FAILED,
                 AuditEvent.AuditSeverity.ERROR, "FAILURE", "auth", data);
    }

    public void logIpVerificationFailed(String source, String clientIp) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("client_ip", clientIp);
        logAudit(AuditEvent.AuditEventType.IP_VERIFICATION_FAILED,
                 AuditEvent.AuditSeverity.ERROR, "BLOCKED", "auth", data);
    }

    public void logProcessingStarted(String source, String alertId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("alert_id", alertId);
        logAudit(AuditEvent.AuditEventType.PROCESSING_STARTED,
                 AuditEvent.AuditSeverity.INFO, null, "worker", data);
    }

    public void logProcessingCompleted(String source, String alertId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("source", source);
        data.put("alert_id", alertId);
        logAudit(AuditEvent.AuditEventType.PROCESSING_COMPLETED,
                 AuditEvent.AuditSeverity.INFO, "SUCCESS", "worker", data);
    }

    public void logProcessingFailed(String source, Map<String, Object> eventData, String errorMessage) {
        Map<String, Object> data = new java.util.HashMap<>(eventData);
        data.put("source", source);
        data.put("error_message", errorMessage);
        logAudit(AuditEvent.AuditEventType.PROCESSING_FAILED,
                 AuditEvent.AuditSeverity.ERROR, "FAILURE", "worker", data);
    }

    private void logAudit(AuditEvent.AuditEventType eventType,
                          AuditEvent.AuditSeverity severity,
                          String status,
                          String step,
                          Map<String, Object> eventData) {
        try {
            AuditEvent event = new AuditEvent(
                eventType, severity,
                org.slf4j.MDC.get("client_id"),
                org.slf4j.MDC.get("alert_id"),
                org.slf4j.MDC.get("phase"),
                org.slf4j.MDC.get("trace_id"),
                step,
                toJson(eventData),
                null,
                status
            );
            if (persistEnabled && repository != null) {
                repository.save(event);
            }
            auditLog.info("Audit event: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to persist audit event {}", eventType, e);
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    node.put(entry.getKey(), (String) value);
                } else if (value instanceof Number) {
                    if (value instanceof Integer || value instanceof Long) {
                        node.put(entry.getKey(), ((Number) value).longValue());
                    } else {
                        node.put(entry.getKey(), ((Number) value).doubleValue());
                    }
                } else if (value instanceof Boolean) {
                    node.put(entry.getKey(), (Boolean) value);
                } else if (value == null) {
                    node.putNull(entry.getKey());
                } else {
                    node.put(entry.getKey(), value.toString());
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"serialization_failed\",\"data\":" + data.toString() + "}";
        }
    }
}
