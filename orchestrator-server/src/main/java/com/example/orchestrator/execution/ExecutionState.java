package com.example.orchestrator.execution;

import com.example.orchestrator.task.TaskPlan;
import com.example.orchestrator.tool.ToolResult;
import java.time.Instant;
import java.util.List;

public record ExecutionState(
    String taskId,
    String projectId,
    String request,
    String requester,
    Instant createdAt,
    ExecutionStatus status,
    String error,
    TaskPlan plan,
    List<ToolResult> toolResults,
    boolean taskSucceeded
) {
    public ExecutionState(String taskId, String projectId, String request, String requester, Instant createdAt, ExecutionStatus status) {
        this(taskId, projectId, request, requester, createdAt, status, null, null, null, false);
    }

    public ExecutionState updateStatus(ExecutionStatus newStatus) {
        return new ExecutionState(taskId, projectId, request, requester, createdAt, newStatus, error, plan, toolResults, taskSucceeded);
    }

    public ExecutionState setError(String error) {
        return new ExecutionState(taskId, projectId, request, requester, createdAt, status, error, plan, toolResults, taskSucceeded);
    }

    public ExecutionState setPlan(TaskPlan plan) {
        return new ExecutionState(taskId, projectId, request, requester, createdAt, status, error, plan, toolResults, taskSucceeded);
    }

    public ExecutionState setToolResults(List<ToolResult> toolResults) {
        return new ExecutionState(taskId, projectId, request, requester, createdAt, status, error, plan, toolResults, taskSucceeded);
    }

    public ExecutionState setTaskSucceeded(boolean taskSucceeded) {
        return new ExecutionState(taskId, projectId, request, requester, createdAt, status, error, plan, toolResults, taskSucceeded);
    }
}