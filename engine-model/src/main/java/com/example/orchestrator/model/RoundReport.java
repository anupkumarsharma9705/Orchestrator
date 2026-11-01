package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Summary report for a single verification round.
 * 
 * @param id Unique identifier
 * @param jobId Job identifier
 * @param roundNum Round number
 * @param buildOk Whether build succeeded
 * @param testsPassed Number of tests passed
 * @param testsFailed Number of tests failed
 * @param coverage Code coverage percentage
 * @param findings List of findings in this round
 * @param fixes List of fixes applied in this round
 * @param costUsd Cost in USD for this round
 * @param durationSec Duration in seconds
 * @param startedAt Round start time
 * @param finishedAt Round finish time
 */
public record RoundReport(
    @NotBlank String id,
    @NotBlank String jobId,
    @Min(1) int roundNum,
    Boolean buildOk,
    int testsPassed,
    int testsFailed,
    BigDecimal coverage,
    List<Finding> findings,
    List<Fix> fixes,
    BigDecimal costUsd,
    int durationSec,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt
) {
    public RoundReport {
        findings = findings != null ? List.copyOf(findings) : List.of();
        fixes = fixes != null ? List.copyOf(fixes) : List.of();
        coverage = coverage != null ? coverage : BigDecimal.ZERO;
        costUsd = costUsd != null ? costUsd : BigDecimal.ZERO;
    }

    /**
     * Checks if this round was successful.
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(buildOk) && testsFailed == 0;
    }

    /**
     * Counts findings by severity.
     */
    public long countFindingsBySeverity(Severity severity) {
        return findings.stream()
            .filter(f -> f.severity() == severity)
            .count();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoundReport that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}