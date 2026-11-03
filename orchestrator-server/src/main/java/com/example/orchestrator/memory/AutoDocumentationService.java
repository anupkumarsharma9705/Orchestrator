package com.example.orchestrator.memory;

import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.execution.ExecutionStatus;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.repair.RepairAttemptRecord;
import com.example.orchestrator.requirement.RequirementSpec;
import com.example.orchestrator.task.TaskPlan;
import com.example.orchestrator.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AutoDocumentationService {

    private final ProjectMemoryService memoryService;
    private final Map<String, Instant> lastUpdate = new ConcurrentHashMap<>();

    public AutoDocumentationService(ProjectMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public void onTaskSubmitted(Project project, String taskId, String spec) {
        updateDevelopmentLog(project, "Task submitted: " + spec.substring(0, Math.min(80, spec.length())));
        updateCurrentState(project, "Task " + taskId + " submitted: " + spec.substring(0, Math.min(60, spec.length())));
    }

    public void onTaskApproved(Project project, String taskId, String approver) {
        updateDevelopmentLog(project, "Task " + taskId + " approved by " + approver);
        updateCurrentState(project, "Task " + taskId + " approved, waiting for execution");
    }

    public void onTaskCompleted(Project project, ExecutionState state) {
        String taskId = state.taskId();
        String spec = state.request();
        
        updateDevelopmentLog(project, "Task " + taskId + " COMPLETED: " + spec.substring(0, Math.min(80, spec.length())));
        updateCurrentState(project, "Last completed: " + spec.substring(0, Math.min(60, spec.length())));
        updateTodoList(project, spec, true);
        
        if (state.toolResults() != null) {
            int toolCount = state.toolResults().size();
            int successCount = (int) state.toolResults().stream().filter(ToolResult::success).count();
            updateDevelopmentLog(project, "Tools executed: " + successCount + "/" + toolCount + " succeeded");
        }
    }

    public void onTaskFailed(Project project, ExecutionState state) {
        String taskId = state.taskId();
        String error = state.error() != null ? state.error() : "Unknown error";
        
        updateDevelopmentLog(project, "Task " + taskId + " FAILED: " + error.substring(0, Math.min(100, error.length())));
        updateCurrentState(project, "Last failed: " + error.substring(0, Math.min(80, error.length())));
        updateKnownIssues(project, "TASK_FAILURE", error, taskId);
    }

    public void onRepairAttempt(Project project, RepairAttemptRecord attempt) {
        updateDevelopmentLog(project, "Repair attempt " + attempt.attemptNumber() + " for " + attempt.repairId() + ": " + attempt.status());
        updateCurrentState(project, "Repair " + attempt.repairId() + " attempt " + attempt.attemptNumber() + " " + attempt.status());
        
        if ("FAILED".equals(attempt.status())) {
            updateKnownIssues(project, "REPAIR_FAILURE", attempt.failureReason(), attempt.repairId());
        }
    }

    public void onDecisionRecorded(Project project, String decisionId, String title, String context, String decision, String consequences) {
        updateDecisions(project, decisionId, title, context, decision, consequences);
        updateDevelopmentLog(project, "Decision recorded: " + title);
    }

    public void onConventionLearned(Project project, String conventionName, String description, String context) {
        updateConventions(project, conventionName, description, context);
        updateDevelopmentLog(project, "Convention learned: " + conventionName);
    }

    public void onSecurityFinding(Project project, String tool, String finding, String severity) {
        updateSecurity(project, tool, finding, severity);
        updateDevelopmentLog(project, "Security finding (" + severity + "): " + finding.substring(0, Math.min(80, finding.length())));
    }

    public void onModelConfigChanged(Project project, String profile, String oldModel, String newModel) {
        updateDevelopmentLog(project, "Model config changed for " + profile + ": " + oldModel + " -> " + newModel);
        updateKnownIssues(project, "MODEL_CONFIG", "Profile " + profile + " model updated from " + oldModel + " to " + newModel, "model-config");
    }

    private void updateDevelopmentLog(Project project, String entry) {
        appendToFile(project, "DEVELOPMENT_LOG.md", formatLogEntry(entry));
    }

    private void updateCurrentState(Project project, String status) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("CURRENT_STATE.md"));
            String updated = updateSection(content, "## Active Work", "- " + status + " (" + timestamp() + ")");
            updated = updateSection(updated, "## Recent Changes", "- " + status + " (" + timestamp() + ")");
            updated = updateSection(updated, "## Last Updated", timestamp());
            memoryService.writeMemoryFile(project, "CURRENT_STATE.md", updated);
        } catch (IOException e) {
            // Log but don't fail the task
        }
    }

    private void updateTodoList(Project project, String spec, boolean completed) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("TODO.md"));
            String taskLine = "- [ ] " + spec.substring(0, Math.min(80, spec.length()));
            if (completed) {
                taskLine = "- [x] " + spec.substring(0, Math.min(80, spec.length())) + " (" + timestamp() + ")";
            }
            String updated = appendToSection(content, "## High Priority", taskLine);
            memoryService.writeMemoryFile(project, "TODO.md", updated);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void updateKnownIssues(Project project, String type, String description, String taskId) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("KNOWN_ISSUES.md"));
            String issueLine = "| " + taskId + " | " + type + " | " + severity(type) + " | OPEN | " + description.substring(0, Math.min(100, description.length())) + " |";
            String updated = appendToTable(content, issueLine);
            memoryService.writeMemoryFile(project, "KNOWN_ISSUES.md", updated);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void updateDecisions(Project project, String decisionId, String title, String context, String decision, String consequences) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("DECISIONS.md"));
            String adr = "\n## " + decisionId + ": " + title + "\n" +
                "**Status**: Accepted\n" +
                "**Date**: " + Instant.now().toString().substring(0, 10) + "\n\n" +
                "**Context**:\n" + context + "\n\n" +
                "**Decision**:\n" + decision + "\n\n" +
                "**Consequences**:\n" + consequences + "\n";
            String updated = content + adr;
            memoryService.writeMemoryFile(project, "DECISIONS.md", updated);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void updateConventions(Project project, String name, String description, String context) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("CONVENTIONS.md"));
            String convention = "\n## " + name + "\n\n" + description + "\n\n**Context**:\n```\n" + context + "\n```\n";
            String updated = appendToSection(content, "## Code Style", convention);
            memoryService.writeMemoryFile(project, "CONVENTIONS.md", updated);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void updateSecurity(Project project, String tool, String finding, String severity) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve("SECURITY.md"));
            String entry = "\n### " + tool + " - " + severity + "\n\n- **Finding**: " + finding + "\n- **Date**: " + timestamp() + "\n- **Status**: OPEN\n";
            String updated = appendToSection(content, "## Phase 3 Security Controls Implemented", entry);
            memoryService.writeMemoryFile(project, "SECURITY.md", updated);
        } catch (IOException e) {
            // Ignore
        }
    }

    private String formatLogEntry(String entry) {
        return "\n## " + Instant.now().toString().substring(0, 10) + "\n- " + entry + "\n";
    }

    private String appendToFile(Project project, String fileName, String entry) {
        try {
            String content = memoryService.readMemoryFile(project.memoryPath().resolve(fileName));
            String updated = content + entry;
            memoryService.writeMemoryFile(project, fileName, updated);
        } catch (IOException e) {
            // Ignore
        }
        return "";
    }

    private String updateSection(String content, String sectionHeader, String newEntry) {
        int index = content.indexOf(sectionHeader);
        if (index == -1) return content;
        int nextHeader = content.indexOf("## ", index + 3);
        if (nextHeader == -1) nextHeader = content.length();
        return content.substring(0, nextHeader) + "\n" + newEntry + "\n" + content.substring(nextHeader);
    }

    private String appendToSection(String content, String sectionHeader, String entry) {
        int index = content.indexOf(sectionHeader);
        if (index == -1) return content + "\n" + entry;
        int nextHeader = content.indexOf("## ", index + 3);
        if (nextHeader == -1) nextHeader = content.length();
        return content.substring(0, nextHeader) + "\n" + entry + "\n" + content.substring(nextHeader);
    }

    private String appendToTable(String content, String row) {
        int index = content.indexOf("|----");
        if (index == -1) return content;
        int nextLine = content.indexOf("\n", index);
        if (nextLine == -1) return content + "\n" + row;
        return content.substring(0, nextLine + 1) + row + "\n" + content.substring(nextLine + 1);
    }

    private String severity(String type) {
        return switch (type) {
            case "TASK_FAILURE", "REPAIR_FAILURE" -> "HIGH";
            case "MODEL_CONFIG" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String timestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
}