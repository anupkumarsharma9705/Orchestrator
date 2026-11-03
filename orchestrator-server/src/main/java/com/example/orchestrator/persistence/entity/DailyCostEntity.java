package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_costs")
public class DailyCostEntity {

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "prompt_tokens")
    private long promptTokens;

    @Column(name = "completion_tokens")
    private long completionTokens;

    @Column(name = "cost_usd_micro")
    private long costUsdMicro;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DailyCostEntity() {
    }

    public DailyCostEntity(LocalDate date, long promptTokens, long completionTokens, long costUsdMicro, Instant updatedAt) {
        this.date = date;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costUsdMicro = costUsdMicro;
        this.updatedAt = updatedAt;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }

    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }

    public long getCostUsdMicro() { return costUsdMicro; }
    public void setCostUsdMicro(long costUsdMicro) { this.costUsdMicro = costUsdMicro; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}