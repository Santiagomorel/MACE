package com.company.rotations.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private AlertType credentialType;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status = AlertStatus.PENDING;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private AlertType detectedSecretType;

    public Alert() {}

    public Alert(UUID id, String providerName, AlertType credentialType, String tenantId,
                 AlertStatus status, String payload, String rawPayload,
                 Instant receivedAt, AlertType detectedSecretType) {
        this.id = id;
        this.providerName = providerName;
        this.credentialType = credentialType;
        this.tenantId = tenantId;
        this.status = status;
        this.payload = payload;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        this.detectedSecretType = detectedSecretType;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public AlertType getCredentialType() { return credentialType; }
    public void setCredentialType(AlertType credentialType) { this.credentialType = credentialType; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public AlertType getDetectedSecretType() { return detectedSecretType; }
    public void setDetectedSecretType(AlertType detectedSecretType) { this.detectedSecretType = detectedSecretType; }

    @Override
    public String toString() {
        return "Alert{id=" + id + ", providerName='" + providerName + "', credentialType=" + credentialType +
               ", tenantId='" + tenantId + "', status=" + status + "}";
    }
}
