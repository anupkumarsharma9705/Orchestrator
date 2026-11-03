package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project_memory")
public class ProjectMemoryEntity {

    public enum MemoryType {
        CONVENTION,
        PATTERN,
        FAILURE,
        SUCCESS,
        DECISION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false, length = 128)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private MemoryType type;

    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public ProjectMemoryEntity() {
    }

    public ProjectMemoryEntity(String projectId, MemoryType type, String pattern,
                               String context, Double confidence) {
        this.projectId = projectId;
        this.type = type;
        this.pattern = pattern;
        this.context = context;
        this.confidence = confidence;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void incrementUsage() {
        this.usageCount++;
        this.lastUsedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}