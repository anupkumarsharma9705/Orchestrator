package com.example.orchestrator.persistence.entity;

import com.example.orchestrator.execution.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "execution_states")
public class ExecutionStateEntity {

    @Id
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "project_id", nullable = false, length = 128)
    private String projectId;

    @Column(name = "request", nullable = false, length = 4000)
    private String request;

    @Column(name = "requester", nullable = false, length = 128)
    private String requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "tool_results_json", columnDefinition = "TEXT")
    private String toolResultsJson;

    @Column(name = "error", length = 4000)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ExecutionStateEntity() {
    }

    public ExecutionStateEntity(String taskId, String projectId, String request, String requester,
                                ExecutionStatus status, Instant createdAt) {
        this.taskId = taskId;
        this.projectId = projectId;
        this.request = request;
        this.requester = requester;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
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

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
        this.updatedAt = Instant.now();
    }

    public String getToolResultsJson() {
        return toolResultsJson;
    }

    public void setToolResultsJson(String toolResultsJson) {
        this.toolResultsJson = toolResultsJson;
        this.updatedAt = Instant.now();
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}