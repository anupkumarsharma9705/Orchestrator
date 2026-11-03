package com.example.orchestrator.audit;

import com.example.orchestrator.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private final Path auditLogPath;
    private final ObjectMapper objectMapper;

    public AuditService(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.auditLogPath = Path.of("orchestrator-audit.log").toAbsolutePath();
        Files.createDirectories(auditLogPath.getParent());
        if (!Files.exists(auditLogPath)) {
            Files.createFile(auditLogPath);
        }
    }

    public void logTaskCreated(String taskId, String projectId, String request) {
        logEvent("TASK_CREATED", Map.of(
            "taskId", taskId,
            "projectId", projectId,
            "request", request
        ));
    }

    public void logTaskApproved(String taskId, String approver, String comment) {
        logEvent("TASK_APPROVED", Map.of(
            "taskId", taskId,
            "approver", approver,
            "comment", comment
        ));
    }

    public void logApprovalRequested(String taskId, String approver, String comment) {
        logEvent("APPROVAL_REQUESTED", Map.of(
            "taskId", taskId,
            "approver", approver,
            "comment", comment
        ));
    }

    public void logTaskRejected(String taskId, String approver, String comment) {
        logEvent("TASK_REJECTED", Map.of(
            "taskId", taskId,
            "approver", approver,
            "comment", comment
        ));
    }

    public void logTaskStarted(String taskId) {
        logEvent("TASK_STARTED", Map.of("taskId", taskId));
    }

    public void logTaskCompleted(String taskId, boolean success, String summary) {
        logEvent("TASK_COMPLETED", Map.of(
            "taskId", taskId,
            "success", success,
            "summary", summary
        ));
    }

    public void logToolExecution(String taskId, ToolResult result) {
        logEvent("TOOL_EXECUTED", Map.of(
            "taskId", taskId,
            "tool", result.toolName(),
            "success", result.success(),
            "durationMs", result.durationMs(),
            "error", result.error()
        ));
    }

    public void logSecurityViolation(String taskId, String violationDetails) {
        logEvent("SECURITY_VIOLATION", Map.of(
            "taskId", taskId,
            "violation", violationDetails,
            "timestamp", Instant.now().toString()
        ));
    }

    public void logRequirementCompiled(String taskId, String requirementSpecJson) {
        logEvent("REQUIREMENT_COMPILED", Map.of(
            "taskId", taskId,
            "spec", requirementSpecJson
        ));
    }

    public void logPlanGenerated(String taskId, String taskPlanJson) {
        logEvent("PLAN_GENERATED", Map.of(
            "taskId", taskId,
            "plan", taskPlanJson
        ));
    }

    private void logEvent(String eventType, Map<String, Object> data) {
        AuditEvent event = new AuditEvent(
            Instant.now(),
            eventType,
            data
        );
        
        try {
            String json = objectMapper.writeValueAsString(event);
            Files.writeString(auditLogPath, json + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }

    private record AuditEvent(
        Instant timestamp,
        String eventType,
        Map<String, Object> data
    ) {}
}