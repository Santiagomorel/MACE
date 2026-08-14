package com.company.rotations.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playbooks")
public class PlaybookStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "playbook_id", nullable = false, unique = true)
    private String playbookId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "content", columnDefinition = "JSONB", nullable = false)
    private String content;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PlaybookStandard() {}

    public PlaybookStandard(UUID id, String playbookId, String version, String content, String provider) {
        this.id = id;
        this.playbookId = playbookId;
        this.version = version;
        this.content = content;
        this.provider = provider;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPlaybookId() { return playbookId; }
    public void setPlaybookId(String playbookId) { this.playbookId = playbookId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "PlaybookStandard{id=" + id + ", playbookId='" + playbookId + "', version='" + version + "'}";
    }
}
