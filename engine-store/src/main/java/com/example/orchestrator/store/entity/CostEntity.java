package com.example.orchestrator.store.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Cost entity for tracking LLM usage costs.
 */
@Entity
@Table(name = "costs")
public class CostEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "round_id", length = 64)
    private String roundId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "profile", nullable = false, length = 50)
    private String profile;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CostEntity() {}

    public CostEntity(String id, String jobId, String roundId, String taskId,
                      String profile, String provider, String model,
                      Integer inputTokens, Integer outputTokens,
                      BigDecimal costUsd, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.roundId = roundId;
        this.taskId = taskId;
        this.profile = profile;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CostEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}