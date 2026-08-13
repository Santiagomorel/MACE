package com.company.rotations.alerting.dlq;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_dlq")
public class AlertDLQEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "source")
    private String source;

    @Column(name = "source_event_id")
    private String sourceEventId;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private DLQStatus status;

    @Column(name = "alert_type")
    private String alertType;

    public enum DLQStatus {
        PENDING,
        RETRYING,
        ARCHIVED
    }

    public AlertDLQEntry() {}

    public AlertDLQEntry(String rawPayload, String errorMessage, String source, String sourceEventId, String alertType) {
        this.rawPayload = rawPayload;
        this.errorMessage = errorMessage;
        this.source = source;
        this.sourceEventId = sourceEventId;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.status = DLQStatus.PENDING;
        this.alertType = alertType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public DLQStatus getStatus() { return status; }
    public void setStatus(DLQStatus status) { this.status = status; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean isMaxRetries(int maxRetries) {
        return retryCount >= maxRetries;
    }
}
