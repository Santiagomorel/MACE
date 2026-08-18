package com.company.rotations.logging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events_logging")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AuditSeverity severity;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "alert_id", length = 100)
    private String alertId;

    @Column(name = "phase", length = 50)
    private String phase;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "step", length = 100)
    private String step;

    @Lob
    @Column(name = "event_data", nullable = false)
    private String eventData;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditEvent() {
        this.createdAt = LocalDateTime.now();
    }

    public AuditEvent(AuditEventType eventType, AuditSeverity severity,
                      String clientId, String alertId, String phase,
                      String traceId, String step, String eventData,
                      Integer durationMs, String status) {
        this();
        this.eventType = eventType;
        this.severity = severity;
        this.clientId = clientId;
        this.alertId = alertId;
        this.phase = phase;
        this.traceId = traceId;
        this.step = step;
        this.eventData = eventData;
        this.durationMs = durationMs;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AuditEventType getEventType() { return eventType; }
    public void setEventType(AuditEventType eventType) { this.eventType = eventType; }

    public AuditSeverity getSeverity() { return severity; }
    public void setSeverity(AuditSeverity severity) { this.severity = severity; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public enum AuditEventType {
        WEBHOOK_RECEIVED,
        ALERT_INGESTED,
        ALERT_DEDUPLICATED,
        PROCESSING_STARTED,
        PROCESSING_COMPLETED,
        PROCESSING_FAILED,
        SIGNATURE_VERIFICATION_FAILED,
        IP_VERIFICATION_FAILED,
        VERIFICATION_STARTED,
        VERIFICATION_COMPLETED,
        RULE_EVALUATED,
        ACTION_EXECUTED,
        DLQ_ENQUEUED,
        DEDUP_HIT
    }

    public enum AuditSeverity {
        INFO,
        WARN,
        ERROR,
        AUDIT
    }
}
