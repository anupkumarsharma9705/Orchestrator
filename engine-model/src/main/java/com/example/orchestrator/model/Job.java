package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A job in the orchestration system.
 * 
 * @param id Unique identifier
 * @param projectId Project identifier
 * @param state Current state of the job
 * @param round Current round number (1-5)
 * @param budgetUsd Budget ceiling in USD
 * @param costUsd Actual cost incurred in USD
 * @param createdAt Timestamp when created
 * @param finishedAt Timestamp when finished (null if still running)
 * @param error Error message if failed
 */
public record Job(
    @NotBlank String id,
    @NotBlank String projectId,
    @NotNull JobState state,
    @Min(1) int round,
    BigDecimal budgetUsd,
    BigDecimal costUsd,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt,
    String error
) {
    public Job {
        budgetUsd = budgetUsd != null ? budgetUsd : BigDecimal.ZERO;
        costUsd = costUsd != null ? costUsd : BigDecimal.ZERO;
    }

    /**
     * Checks if the job is in a terminal state.
     */
    @JsonIgnore
    public boolean isTerminal() {
        return state == JobState.COMPLETED || state == JobState.FAILED || state == JobState.CANCELLED;
    }

    /**
     * Checks if the job is currently running.
     */
    @JsonIgnore
    public boolean isRunning() {
        return state == JobState.BUILDING || state == JobState.VERIFYING || state == JobState.FIXING;
    }

    /**
     * Checks if budget has been exceeded.
     */
    @JsonIgnore
    public boolean isBudgetExceeded() {
        return costUsd != null && budgetUsd != null && costUsd.compareTo(budgetUsd) > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Job that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}