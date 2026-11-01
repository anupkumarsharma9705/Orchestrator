package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Objects;

/**
 * Daily cost aggregation.
 * 
 * @param date Date (primary key)
 * @param promptTokens Total prompt tokens for the day
 * @param completionTokens Total completion tokens for the day
 * @param costUsd Total cost in USD for the day
 * @param updatedAt Last update timestamp
 */
public record DailyCost(
    @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
    long promptTokens,
    long completionTokens,
    @NotNull BigDecimal costUsd,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DailyCost that)) return false;
        return Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date);
    }
}