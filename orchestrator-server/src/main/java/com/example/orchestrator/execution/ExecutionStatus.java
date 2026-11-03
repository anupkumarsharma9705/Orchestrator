package com.example.orchestrator.execution;

import com.example.orchestrator.task.TaskPlan;
import com.example.orchestrator.tool.ToolResult;
import java.time.Instant;
import java.util.List;

public enum ExecutionStatus {
    PENDING_APPROVAL,
    COMPILING_REQUIREMENTS,
    PLANNING,
    WAITING_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    REJECTED
}