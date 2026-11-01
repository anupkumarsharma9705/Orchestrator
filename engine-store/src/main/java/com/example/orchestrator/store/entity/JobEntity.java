package com.example.orchestrator.store.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Job entity for tracking execution jobs.
 */
@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "total_duration_sec")
    private Integer totalDurationSec = 0;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    public JobEntity() {}

    public JobEntity(String id, String projectName, String description, String status,
                     Instant createdAt, Instant startedAt, Instant finishedAt,
                     BigDecimal totalCostUsd, Integer totalDurationSec, String configJson) {
        this.id = id;
        this.projectName = projectName;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalCostUsd = totalCostUsd;
        this.totalDurationSec = totalDurationSec;
        this.configJson = configJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }

    public Integer getTotalDurationSec() { return totalDurationSec; }
    public void setTotalDurationSec(Integer totalDurationSec) { this.totalDurationSec = totalDurationSec; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}