package com.company.rotations.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credentials")
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private CredentialType credentialType;

    @Column(name = "provider_arn")
    private String providerArn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CredentialStatus status = CredentialStatus.ACTIVE;

    @Column(name = "key_id")
    private String keyId;

    @JsonProperty("ttl_remaining_seconds")
    @Column(name = "ttl_remaining_seconds")
    private Long ttlRemainingSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_prefix")
    private CredentialPrefix credentialPrefix;

    public Credential() {}

    public Credential(UUID id, String tenantId, CredentialType credentialType,
                      String providerArn, CredentialStatus status, String keyId,
                      Long ttlRemainingSeconds, CredentialPrefix credentialPrefix) {
        this.id = id;
        this.tenantId = tenantId;
        this.credentialType = credentialType;
        this.providerArn = providerArn;
        this.status = status;
        this.keyId = keyId;
        this.ttlRemainingSeconds = ttlRemainingSeconds;
        this.credentialPrefix = credentialPrefix;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public CredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(CredentialType credentialType) { this.credentialType = credentialType; }

    public String getProviderArn() { return providerArn; }
    public void setProviderArn(String providerArn) { this.providerArn = providerArn; }

    public CredentialStatus getStatus() { return status; }
    public void setStatus(CredentialStatus status) { this.status = status; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public Long getTtlRemainingSeconds() { return ttlRemainingSeconds; }
    public void setTtlRemainingSeconds(Long ttlRemainingSeconds) { this.ttlRemainingSeconds = ttlRemainingSeconds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public CredentialPrefix getCredentialPrefix() { return credentialPrefix; }
    public void setCredentialPrefix(CredentialPrefix credentialPrefix) { this.credentialPrefix = credentialPrefix; }

    public enum CredentialType {
        ACCESS_KEY,
        SESSION_TOKEN,
        ROOT_ACCOUNT_IAM_ROLE
    }

    public enum CredentialStatus {
        ACTIVE,
        INACTIVE
    }

    public enum CredentialPrefix {
        AKIA,
        ASIA,
        ROOT
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "Credential{id=" + id + ", tenantId='" + tenantId + "', credentialType=" + credentialType +
               ", status=" + status + ", keyId='" + keyId + "'}";
    }
}
