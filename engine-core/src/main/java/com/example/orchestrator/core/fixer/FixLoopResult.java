package com.example.orchestrator.core.fixer;

import com.example.orchestrator.model.RoundReport;
import com.example.orchestrator.model.Finding;
import com.example.orchestrator.model.FindingStatus;
import com.example.orchestrator.model.Severity;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Result of the complete fix loop execution.
 */
public record FixLoopResult(
    List<RoundReport> roundReports,
    int roundsCompleted,
    Duration totalDuration,
    Path workspacePath,
    boolean buildOk,
    boolean testsPassed,
    double finalCoverage,
    List<Finding> finalFindings,
    Map<String, Object> metadata
) {
    public FixLoopResult {
        roundReports = roundReports != null ? List.copyOf(roundReports) : List.of();
        finalFindings = finalFindings != null ? List.copyOf(finalFindings) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Checks if the fix loop achieved a clean state.
     */
    public boolean isClean() {
        return buildOk && testsPassed 
            && finalFindings.stream()
                .filter(f -> f.status() == FindingStatus.OPEN)
                .noneMatch(f -> f.severity() == Severity.CRITICAL
                             || f.severity() == Severity.HIGH);
    }

    /**
     * Gets the count of open high/critical findings.
     */
    public long openHighCriticalCount() {
        return finalFindings.stream()
            .filter(f -> f.status() == FindingStatus.OPEN)
            .filter(f -> f.severity() == Severity.CRITICAL
                      || f.severity() == Severity.HIGH)
            .count();
    }

    /**
     * Gets summary of findings by status.
     */
    public Map<String, Long> findingsByStatus() {
        return finalFindings.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                f -> f.status().name(),
                java.util.stream.Collectors.counting()
            ));
    }
}