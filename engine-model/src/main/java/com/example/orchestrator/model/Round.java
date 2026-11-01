package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single round in the fix loop.
 * 
 * @param id Unique identifier
 * @param jobId Job identifier
 * @param roundNum Round number (1-5)
 * @param buildOk Whether the build succeeded
 * @param testsPassed Number of tests passed
 * @param testsFailed Number of tests failed
 * @param coverage Code coverage percentage
 * @param costUsd Cost incurred in this round
 * @param durationSec Duration in seconds
 * @param startedAt Timestamp when round started
 * @param finishedAt Timestamp when round finished
 */
public record Round(
    @NotBlank String id,
    @NotBlank String jobId,
    @Min(1) int roundNum,
    Boolean buildOk,
    int testsPassed,
    int testsFailed,
    BigDecimal coverage,
    BigDecimal costUsd,
    int durationSec,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt
) {
    public Round {
        coverage = coverage != null ? coverage : BigDecimal.ZERO;
        costUsd = costUsd != null ? costUsd : BigDecimal.ZERO;
    }

    /**
     * Checks if this round was successful.
     */
    @JsonIgnore
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(buildOk) && testsFailed == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Round that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}