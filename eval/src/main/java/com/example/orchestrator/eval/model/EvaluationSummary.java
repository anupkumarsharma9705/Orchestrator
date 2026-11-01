package com.example.orchestrator.eval.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Summary of an evaluation run across multiple golden specs.
 */
public record EvaluationSummary(
    String runId,
    String runName,
    int totalSpecs,
    int passedSpecs,
    int failedSpecs,
    int skippedSpecs,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Duration totalDuration,
    BigDecimal totalCostUsd,
    double avgCoveragePercent,
    double avgTestPassRate,
    int totalHighCriticalFindings,
    int specsWithQualityBar,
    List<EvaluationResult> results,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt,
    Map<String, Object> metadata
) {
    public EvaluationSummary {
        results = results != null ? List.copyOf(results) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Overall success rate (specs passed / total specs).
     */
    public double getSuccessRate() {
        return totalSpecs > 0 ? (double) passedSpecs / totalSpecs : 0.0;
    }

    /**
     * Quality rate (specs meeting quality bar / total specs).
     */
    public double getQualityRate() {
        return totalSpecs > 0 ? (double) specsWithQualityBar / totalSpecs : 0.0;
    }

    /**
     * Results grouped by category.
     */
    public Map<String, List<EvaluationResult>> getResultsByCategory() {
        return results.stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> r.metadata().getOrDefault("category", "UNKNOWN").toString()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvaluationSummary that)) return false;
        return Objects.equals(runId, that.runId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId);
    }
}