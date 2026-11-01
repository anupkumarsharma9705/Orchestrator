package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Global cost aggregates (singleton).
 * 
 * @param id Always "singleton"
 * @param totalPromptTokens Total prompt tokens across all time
 * @param totalCompletionTokens Total completion tokens across all time
 * @param totalCostUsd Total cost in USD across all time
 * @param dailyCostUsd Cost in USD for current day
 * @param lastReset When the daily counter was last reset
 * @param updatedAt Last update timestamp
 */
public record CostAggregate(
    @NotBlank String id,
    @NotNull long totalPromptTokens,
    @NotNull long totalCompletionTokens,
    @NotNull BigDecimal totalCostUsd,
    @NotNull BigDecimal dailyCostUsd,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastReset,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public CostAggregate {
        totalPromptTokens = Math.max(0, totalPromptTokens);
        totalCompletionTokens = Math.max(0, totalCompletionTokens);
        totalCostUsd = totalCostUsd != null ? totalCostUsd : BigDecimal.ZERO;
        dailyCostUsd = dailyCostUsd != null ? dailyCostUsd : BigDecimal.ZERO;
    }

    public long totalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CostAggregate that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}