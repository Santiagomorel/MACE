package com.company.rotations.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_rules")
public class ClientRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "version", nullable = false)
    private int version;

    @Lob
    @Column(name = "drl_content", columnDefinition = "BYTEA", nullable = false)
    private byte[] drlContent;

    @Column(name = "drl_size_bytes")
    private int drlSizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "manual_override_by_client")
    private boolean manualOverrideByClient;

    @Column(name = "override_by_user")
    private String overrideByUser;

    @Column(name = "override_timestamp")
    private Instant overrideTimestamp;

    @Column(name = "playbook_id")
    private String playbookId;

    @Column(name = "active")
    private boolean active = true;

    public ClientRule() {}

    public ClientRule(UUID id, String tenantId, int version, byte[] drlContent, String playbookId) {
        this.id = id;
        this.tenantId = tenantId;
        this.version = version;
        this.drlContent = drlContent;
        this.drlSizeBytes = drlContent != null ? drlContent.length : 0;
        this.playbookId = playbookId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public byte[] getDrlContent() { return drlContent; }
    public void setDrlContent(byte[] drlContent) {
        this.drlContent = drlContent;
        this.drlSizeBytes = drlContent != null ? drlContent.length : 0;
    }

    public int getDrlSizeBytes() { return drlSizeBytes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isManualOverrideByClient() { return manualOverrideByClient; }
    public void setManualOverrideByClient(boolean manualOverrideByClient) { this.manualOverrideByClient = manualOverrideByClient; }

    public String getOverrideByUser() { return overrideByUser; }
    public void setOverrideByUser(String overrideByUser) { this.overrideByUser = overrideByUser; }

    public Instant getOverrideTimestamp() { return overrideTimestamp; }
    public void setOverrideTimestamp(Instant overrideTimestamp) { this.overrideTimestamp = overrideTimestamp; }

    public String getPlaybookId() { return playbookId; }
    public void setPlaybookId(String playbookId) { this.playbookId = playbookId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "ClientRule{id=" + id + ", tenantId='" + tenantId + "', version=" + version +
               ", drlSize=" + drlSizeBytes + " bytes, active=" + active + "}";
    }
}
