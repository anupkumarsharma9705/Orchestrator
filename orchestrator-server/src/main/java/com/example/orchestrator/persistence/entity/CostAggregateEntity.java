package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cost_aggregates")
public class CostAggregateEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "total_prompt_tokens")
    private long totalPromptTokens;

    @Column(name = "total_completion_tokens")
    private long totalCompletionTokens;

    @Column(name = "total_cost_usd_micro")
    private long totalCostUsdMicro;

    @Column(name = "daily_cost_usd_micro")
    private long dailyCostUsdMicro;

    @Column(name = "last_reset", nullable = false)
    private Instant lastReset;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CostAggregateEntity() {
    }

    public CostAggregateEntity(String id, long totalPromptTokens, long totalCompletionTokens,
                               long totalCostUsdMicro, long dailyCostUsdMicro,
                               Instant lastReset, Instant updatedAt) {
        this.id = id;
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
        this.totalCostUsdMicro = totalCostUsdMicro;
        this.dailyCostUsdMicro = dailyCostUsdMicro;
        this.lastReset = lastReset;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(long totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }

    public long getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(long totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }

    public long getTotalCostUsdMicro() { return totalCostUsdMicro; }
    public void setTotalCostUsdMicro(long totalCostUsdMicro) { this.totalCostUsdMicro = totalCostUsdMicro; }

    public long getDailyCostUsdMicro() { return dailyCostUsdMicro; }
    public void setDailyCostUsdMicro(long dailyCostUsdMicro) { this.dailyCostUsdMicro = dailyCostUsdMicro; }

    public Instant getLastReset() { return lastReset; }
    public void setLastReset(Instant lastReset) { this.lastReset = lastReset; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}