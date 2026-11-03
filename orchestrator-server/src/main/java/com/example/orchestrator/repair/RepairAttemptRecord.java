package com.example.orchestrator.repair;

import com.example.orchestrator.verification.VerificationResult;
import java.time.Instant;
import java.util.List;

public record RepairAttemptRecord(
    String attemptId,
    String repairId,
    int attemptNumber,
    String taskId,
    String status,
    String failureReason,
    List<VerificationResult.StepResult> steps,
    long durationMs,
    Instant timestamp
) {
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}