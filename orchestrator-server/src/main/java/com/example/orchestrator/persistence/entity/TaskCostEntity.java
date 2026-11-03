package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "task_costs")
public class TaskCostEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "prompt_tokens")
    private long promptTokens;

    @Column(name = "completion_tokens")
    private long completionTokens;

    @Column(name = "cost_usd_micro")
    private long costUsdMicro;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    public TaskCostEntity() {
    }

    public TaskCostEntity(String id, String taskId, String model, String provider,
                          long promptTokens, long completionTokens, long costUsdMicro,
                          Instant startTime, Instant lastUpdate) {
        this.id = id;
        this.taskId = taskId;
        this.model = model;
        this.provider = provider;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costUsdMicro = costUsdMicro;
        this.startTime = startTime;
        this.lastUpdate = lastUpdate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }

    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }

    public long getCostUsdMicro() { return costUsdMicro; }
    public void setCostUsdMicro(long costUsdMicro) { this.costUsdMicro = costUsdMicro; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(Instant lastUpdate) { this.lastUpdate = lastUpdate; }
}