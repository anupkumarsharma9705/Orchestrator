package com.example.orchestrator.verification;

import com.example.orchestrator.tool.ToolResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class VerificationResult {

    private final boolean overallSuccess;
    private final Map<String, StepResult> steps;
    private final Instant startedAt;
    private final Instant completedAt;
    private final long durationMs;
    private final String repairId;

    private VerificationResult(Builder builder) {
        this.overallSuccess = builder.overallSuccess;
        this.steps = Map.copyOf(builder.steps);
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.durationMs = builder.completedAt.toEpochMilli() - builder.startedAt.toEpochMilli();
        this.repairId = builder.repairId;
    }

    public boolean isOverallSuccess() { return overallSuccess; }
    public Map<String, StepResult> getSteps() { return steps; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
    public String getRepairId() { return repairId; }

    public String getFailureSummary() {
        return steps.values().stream()
            .filter(s -> !s.success())
            .map(s -> s.name() + ": " + (s.error() != null ? s.error() : "failed"))
            .collect(Collectors.joining("; "));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean overallSuccess = true;
        private final Map<String, StepResult> steps = new LinkedHashMap<>();
        private Instant startedAt = Instant.now();
        private Instant completedAt = Instant.now();
        private String repairId = "VER-" + UUID.randomUUID().toString().substring(0, 8);

        public Builder addStep(String name, com.example.orchestrator.tool.ToolResult result) {
            return addStep(name, result, Severity.MEDIUM);
        }

        public Builder addStep(String name, com.example.orchestrator.tool.ToolResult result, Severity severity) {
            StepResult step = new StepResult(
                name,
                result.success(),
                result.output(),
                result.error(),
                result.durationMs(),
                result.timestamp(),
                severity
            );
            steps.put(name, step);
            if (!result.success()) {
                overallSuccess = false;
            }
            return this;
        }

        public Builder repairId(String repairId) {
            this.repairId = repairId;
            return this;
        }

        public VerificationResult build() {
            this.completedAt = Instant.now();
            return new VerificationResult(this);
        }
    }

    public enum Severity {
        CRITICAL,  // Security, data loss, system crash
        HIGH,      // Functional breakage, build failures
        MEDIUM,    // Test failures, performance regressions
        LOW        // Style, warnings, minor issues
    }

    public record StepResult(
        String name,
        boolean success,
        String output,
        String error,
        long durationMs,
        Instant timestamp,
        Severity severity
    ) {
        public StepResult(String name, boolean success, String output, String error, long durationMs, Instant timestamp) {
            this(name, success, output, error, durationMs, timestamp, Severity.MEDIUM);
        }
    }
}