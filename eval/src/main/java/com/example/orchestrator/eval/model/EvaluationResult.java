package com.example.orchestrator.eval.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a single golden spec.
 * 
 * @param specId Golden spec ID that was evaluated
 * @param jobId Job ID created for this evaluation
 * @param success Whether the evaluation completed successfully
 * @param duration Total time taken
 * @param buildOk Whether the generated project compiles
 * @param testsPassed Number of tests that passed
 * @param testsFailed Number of tests that failed
 * @param coveragePercent Code coverage percentage
 * @param findingsCount Total findings from verification
 * @param highCriticalFindings Count of HIGH/CRITICAL findings
 * @param costUsd Total cost in USD
 * @param roundsCompleted Number of fix loop rounds completed
 * @param entitiesGenerated List of entities that were generated
 * @param endpointsGenerated List of endpoints that were generated
 * @param errorMessage Error message if failed
 * @param startedAt Evaluation start time
 * @param finishedAt Evaluation finish time
 * @param metadata Additional metadata
 */
public record EvaluationResult(
    @NotBlank String specId,
    String jobId,
    boolean success,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Duration duration,
    boolean buildOk,
    int testsPassed,
    int testsFailed,
    BigDecimal coveragePercent,
    int findingsCount,
    int highCriticalFindings,
    BigDecimal costUsd,
    int roundsCompleted,
    @Size(min = 1) List<String> entitiesGenerated,
    @Size(min = 1) List<String> endpointsGenerated,
    String errorMessage,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt,
    Map<String, Object> metadata
) {
    public EvaluationResult {
        entitiesGenerated = entitiesGenerated != null ? List.copyOf(entitiesGenerated) : List.of();
        endpointsGenerated = endpointsGenerated != null ? List.copyOf(endpointsGenerated) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Checks if this evaluation met the minimum quality bar.
     */
    public boolean meetsQualityBar() {
        return success && buildOk && testsFailed == 0 && highCriticalFindings == 0;
    }

    /**
     * Gets the test pass rate.
     */
    public double getTestPassRate() {
        int total = testsPassed + testsFailed;
        return total > 0 ? (double) testsPassed / total : 0.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvaluationResult that)) return false;
        return Objects.equals(specId, that.specId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specId);
    }
}