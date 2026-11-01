package com.example.orchestrator.verify;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Result of a verification run.
 */
public record VerificationResult(
    boolean buildOk,
    boolean testsPassed,
    double coveragePercent,
    List<Finding> findings,
    Duration duration,
    String scannerOutput
) {
    public boolean hasHighOrCriticalFindings() {
        return findings.stream().anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH);
    }
    
    public long countBySeverity(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }
}