package com.company.rotations.logging.service;

import com.company.rotations.logging.model.AuditEvent;
import com.company.rotations.logging.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
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
            repository.save(event);
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
