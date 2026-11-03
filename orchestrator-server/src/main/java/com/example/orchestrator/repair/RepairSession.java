package com.example.orchestrator.repair;

import com.example.orchestrator.project.Project;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public record RepairSession(
    String repairId,
    String taskId,
    Project project,
    int maxAttempts,
    int attemptTimeoutMinutes,
    int totalTimeoutMinutes,
    Instant startedAt,
    AtomicInteger currentAttempt,
    RepairStatus status,
    Instant attemptStartedAt,
    String failureReason
) {
    public enum RepairStatus {
        PENDING,
        ATTEMPT_IN_PROGRESS,
        ATTEMPT_FAILED,
        COMPLETED_SUCCESS,
        COMPLETED_FAILED,
        TIMEOUT,
        CANCELLED
    }

    public RepairSession(String repairId, String taskId, Project project, int maxAttempts, int attemptTimeoutMinutes, int totalTimeoutMinutes, Instant startedAt) {
        this(repairId, taskId, project, maxAttempts, 10, 30, startedAt, new AtomicInteger(0), RepairStatus.PENDING, null, null);
    }

    public RepairSession setCurrentAttempt(int attempt) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, new AtomicInteger(attempt), RepairStatus.ATTEMPT_IN_PROGRESS, Instant.now(), failureReason);
    }

    public RepairSession markAttemptStarted(Instant timestamp) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, RepairStatus.ATTEMPT_IN_PROGRESS, timestamp, failureReason);
    }

    public RepairSession markAttemptFailed(String reason) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, RepairStatus.ATTEMPT_FAILED, attemptStartedAt, reason);
    }

    public RepairSession markCompleted(boolean success) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, 
            success ? RepairStatus.COMPLETED_SUCCESS : RepairStatus.COMPLETED_FAILED, attemptStartedAt, failureReason);
    }

    public RepairSession markTimeout(String reason) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, RepairStatus.TIMEOUT, attemptStartedAt, reason);
    }

    public RepairSession markCancelled() {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, RepairStatus.CANCELLED, attemptStartedAt, "Cancelled by user");
    }

    public RepairSession markFailed(String reason) {
        return new RepairSession(repairId, taskId, project, maxAttempts, attemptTimeoutMinutes, totalTimeoutMinutes, startedAt, currentAttempt, RepairStatus.COMPLETED_FAILED, attemptStartedAt, reason);
    }
}