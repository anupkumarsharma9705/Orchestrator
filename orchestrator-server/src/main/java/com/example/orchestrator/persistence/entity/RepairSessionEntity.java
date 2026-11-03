package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "repair_sessions")
public class RepairSessionEntity {

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    @Id
    @Column(name = "repair_id", nullable = false, length = 64)
    private String repairId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "project_id", nullable = false, length = 128)
    private String projectId;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "current_attempt")
    private Integer currentAttempt;

    @Column(name = "attempt_timeout_minutes")
    private Integer attemptTimeoutMinutes;

    @Column(name = "total_timeout_minutes")
    private Integer totalTimeoutMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Column(name = "error", length = 4000)
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public RepairSessionEntity() {
    }

    public RepairSessionEntity(String repairId, String taskId, String projectId,
                               Integer maxAttempts, Integer attemptTimeoutMinutes,
                               Integer totalTimeoutMinutes, Instant startedAt) {
        this.repairId = repairId;
        this.taskId = taskId;
        this.projectId = projectId;
        this.maxAttempts = maxAttempts;
        this.currentAttempt = 0;
        this.attemptTimeoutMinutes = attemptTimeoutMinutes;
        this.totalTimeoutMinutes = totalTimeoutMinutes;
        this.status = Status.RUNNING;
        this.startedAt = startedAt;
    }

    public String getRepairId() {
        return repairId;
    }

    public void setRepairId(String repairId) {
        this.repairId = repairId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getCurrentAttempt() {
        return currentAttempt;
    }

    public void setCurrentAttempt(Integer currentAttempt) {
        this.currentAttempt = currentAttempt;
    }

    public Integer getAttemptTimeoutMinutes() {
        return attemptTimeoutMinutes;
    }

    public void setAttemptTimeoutMinutes(Integer attemptTimeoutMinutes) {
        this.attemptTimeoutMinutes = attemptTimeoutMinutes;
    }

    public Integer getTotalTimeoutMinutes() {
        return totalTimeoutMinutes;
    }

    public void setTotalTimeoutMinutes(Integer totalTimeoutMinutes) {
        this.totalTimeoutMinutes = totalTimeoutMinutes;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.COMPLETED || status == Status.FAILED ||
            status == Status.CANCELLED || status == Status.TIMEOUT) {
            this.completedAt = Instant.now();
        }
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}