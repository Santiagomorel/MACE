package com.company.rotations.verification.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "verification_results_entity")
public class VerificationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "identity_arn")
    private String identityArn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VerificationStatus status;

    @Column(name = "action_matrix")
    private Set<String> actionMatrix;

    @Column(name = "last_used_date")
    private String lastUsedDate;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "tenant_id")
    private String tenantId;

    public VerificationResultEntity() {}

    public VerificationResultEntity(String accountId, String identityArn, VerificationStatus status,
                                     Set<String> actionMatrix, String lastUsedDate, String reason,
                                     String eventId, String tenantId) {
        this.accountId = accountId;
        this.identityArn = identityArn;
        this.status = status;
        this.actionMatrix = actionMatrix;
        this.lastUsedDate = lastUsedDate;
        this.reason = reason;
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.verifiedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getIdentityArn() { return identityArn; }
    public void setIdentityArn(String identityArn) { this.identityArn = identityArn; }

    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }

    public Set<String> getActionMatrix() { return actionMatrix; }
    public void setActionMatrix(Set<String> actionMatrix) { this.actionMatrix = actionMatrix; }

    public String getLastUsedDate() { return lastUsedDate; }
    public void setLastUsedDate(String lastUsedDate) { this.lastUsedDate = lastUsedDate; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public VerificationResult toDomain() {
        VerificationResult result = new VerificationResult();
        result.setAccountId(accountId);
        result.setIdentityArn(identityArn);
        result.setStatus(status);
        result.setActionMatrix(actionMatrix);
        result.setLastUsedDate(lastUsedDate);
        result.setVerifiedAt(verifiedAt);
        result.setReason(reason);
        return result;
    }
}
