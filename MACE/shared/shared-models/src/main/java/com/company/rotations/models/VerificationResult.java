package com.company.rotations.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_results")
public class VerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "reason")
    private String reason;

    @Column(name = "severity_scope")
    private String severityScope;

    @Column(name = "blast_radius")
    private String blastRadius;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type")
    private AlertType credentialType;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public VerificationResult() {}

    public VerificationResult(UUID alertId, boolean verified, String reason,
                              String severityScope, String blastRadius,
                              AlertType credentialType, String tenantId, String provider) {
        this.alertId = alertId;
        this.verified = verified;
        this.reason = reason;
        this.severityScope = severityScope;
        this.blastRadius = blastRadius;
        this.credentialType = credentialType;
        this.tenantId = tenantId;
        this.provider = provider;
        this.timestamp = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSeverityScope() { return severityScope; }
    public void setSeverityScope(String severityScope) { this.severityScope = severityScope; }

    public String getBlastRadius() { return blastRadius; }
    public void setBlastRadius(String blastRadius) { this.blastRadius = blastRadius; }

    public AlertType getCredentialType() { return credentialType; }
    public void setCredentialType(AlertType credentialType) { this.credentialType = credentialType; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
