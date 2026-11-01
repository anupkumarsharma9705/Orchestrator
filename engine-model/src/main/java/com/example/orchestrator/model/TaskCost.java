package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Cost tracking for a single LLM task/call.
 * 
 * @param id Unique identifier
 * @param taskId Task identifier this cost is associated with
 * @param model Model name (e.g., "nvidia/nemotron-3-ultra")
 * @param provider Provider name (e.g., "nvidia", "openrouter")
 * @param promptTokens Number of prompt tokens
 * @param completionTokens Number of completion tokens
 * @param costUsd Cost in USD
 * @param startTime When the call started
 * @param lastUpdate Last update timestamp
 */
public record TaskCost(
    @NotBlank String id,
    @NotBlank String taskId,
    @NotBlank String model,
    @NotBlank String provider,
    long promptTokens,
    long completionTokens,
    @NotNull BigDecimal costUsd,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastUpdate
) {
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskCost that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}