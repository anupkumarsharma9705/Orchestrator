package com.example.orchestrator.execution;

import com.example.orchestrator.audit.AsyncMdcContext;
import com.example.orchestrator.security.SecurityEnforcer;
import com.example.orchestrator.security.SecurityPolicy;
import com.example.orchestrator.security.SecurityPolicy.SecurityValidationResult;
import com.example.orchestrator.security.SecurityPolicy.SecurityViolation;
import com.example.orchestrator.approval.ApprovalDecision;
import com.example.orchestrator.approval.ApprovalService;
import com.example.orchestrator.audit.AuditService;
import com.example.orchestrator.context.ContextBundle;
import com.example.orchestrator.context.ContextManager;
import com.example.orchestrator.memory.AutoDocumentationService;
import com.example.orchestrator.memory.LearningExtractor;
import com.example.orchestrator.memory.MemoryBundle;
import com.example.orchestrator.memory.ProjectMemoryService;
import com.example.orchestrator.model.router.ModelProfile;
import com.example.orchestrator.model.router.ModelRouter;
import com.example.orchestrator.model.router.ToolCallParser;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import com.example.orchestrator.permission.PermissionManager;
import com.example.orchestrator.persistence.entity.ExecutionStateEntity;
import com.example.orchestrator.persistence.repo.ExecutionStateRepository;
import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.execution.ExecutionStatus;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.project.ProjectManager;
import com.example.orchestrator.requirement.RequirementCompiler;
import com.example.orchestrator.requirement.RequirementSpec;
import com.example.orchestrator.task.Task;
import com.example.orchestrator.task.TaskPlan;
import com.example.orchestrator.task.TaskPlanner;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * ExecutionEngine coordinates the execution of tasks through the orchestration pipeline.
 */
@Service
public class ExecutionEngine {

    private final ProjectManager projectManager;
    private final ProjectMemoryService memoryService;
    private final ContextManager contextManager;
    private final RequirementCompiler requirementCompiler;
    private final TaskPlanner taskPlanner;
    private final ApprovalService approvalService;
    private final ToolRegistry toolRegistry;
    private final AuditService auditService;
    private final PermissionManager permissionManager;
    private final ModelRouter modelRouter;
    private final ExecutionStateRepository stateRepository;
    private final LearningExtractor learningExtractor;
    private final SecurityEnforcer securityEnforcer;
    private final AutoDocumentationService autoDocumentationService;

    public ExecutionEngine(
            ProjectManager projectManager,
            ProjectMemoryService memoryService,
            ContextManager contextManager,
            RequirementCompiler requirementCompiler,
            TaskPlanner taskPlanner,
            ApprovalService approvalService,
            ToolRegistry toolRegistry,
            AuditService auditService,
            PermissionManager permissionManager,
            ModelRouter modelRouter,
            ExecutionStateRepository stateRepository,
            LearningExtractor learningExtractor,
            SecurityEnforcer securityEnforcer,
            AutoDocumentationService autoDocumentationService
    ) {
        this.projectManager = projectManager;
        this.memoryService = memoryService;
        this.contextManager = contextManager;
        this.requirementCompiler = requirementCompiler;
        this.taskPlanner = taskPlanner;
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.permissionManager = permissionManager;
        this.modelRouter = modelRouter;
        this.stateRepository = stateRepository;
        this.learningExtractor = learningExtractor;
        this.securityEnforcer = securityEnforcer;
        this.autoDocumentationService = autoDocumentationService;
    }

    public String submitTask(String projectId, String request, String requester) {
        Project project = projectManager.get(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ExecutionStateEntity entity = new ExecutionStateEntity(
                taskId, projectId, request, requester,
                ExecutionStatus.PENDING_APPROVAL, Instant.now()
        );
        stateRepository.save(entity);

        auditService.logTaskCreated(taskId, projectId, request);

        // Auto-documentation: task submitted
        autoDocumentationService.onTaskSubmitted(project, taskId, request);

        // Start async execution with MDC context propagation
        CompletableFuture.runAsync(AsyncMdcContext.wrap(() -> executeTaskAsync(taskId, project, request)));

        return taskId;
    }

    private void executeTaskAsync(String taskId, Project project, String request) {
        ExecutionStateEntity entity = stateRepository.findById(taskId).orElse(null);
        if (entity == null) return;

        try {
            entity.setStatus(ExecutionStatus.COMPILING_REQUIREMENTS);
            stateRepository.save(entity);

            // Load memory
            MemoryBundle memory = memoryService.loadMemory(project);

            // Select context
            ContextBundle context = contextManager.selectContext(project, request, memory);

            // Compile requirements
            RequirementSpec requirements = requirementCompiler.compile(context).block();
            auditService.logRequirementCompiled(taskId, toJson(requirements));

            entity.setStatus(ExecutionStatus.PLANNING);
            stateRepository.save(entity);

            // Generate plan
            TaskPlan plan = taskPlanner.plan(requirements, context).block();
            auditService.logPlanGenerated(taskId, toJson(plan));

            entity.setPlanJson(toJson(plan));
            entity.setStatus(ExecutionStatus.WAITING_APPROVAL);
            stateRepository.save(entity);

            // Request approval
            approvalService.requestApproval(taskId);
            auditService.logApprovalRequested(taskId, "system", "Auto-approval for system tasks");

            // Wait for approval (with timeout) - auto-approve for "system"
            ApprovalDecision decision = approvalService.waitForApproval(taskId, 30 * 60 * 1000); // 30 min

            if (decision == null || decision.decision() == ApprovalDecision.Decision.REJECTED) {
                entity.setStatus(ExecutionStatus.REJECTED);
                stateRepository.save(entity);
                auditService.logTaskRejected(taskId, decision != null ? decision.approver() : "timeout",
                        decision != null ? decision.comment() : "Approval timeout");
                return;
            }

            auditService.logTaskApproved(taskId, decision.approver(), decision.comment());

            // Auto-documentation: task approved
            autoDocumentationService.onTaskApproved(project, taskId, decision.approver());

            // Execute plan with all tools (Phase 2: CONTROLLED tools now available)
            entity.setStatus(ExecutionStatus.EXECUTING);
            stateRepository.save(entity);
            auditService.logTaskStarted(taskId);

            // Block at the async boundary - executePlan is now reactive
            List<ToolResult> toolResults = executePlan(project, plan, taskId).block();

            entity.setToolResultsJson(toJson(toolResults));
            entity.setStatus(ExecutionStatus.COMPLETED);
            stateRepository.save(entity);
            auditService.logTaskCompleted(taskId, true, "Plan executed successfully");

            // Auto-documentation: task completed
            ExecutionState completedState = toDomain(entity);
            autoDocumentationService.onTaskCompleted(project, completedState);

            // Extract learnings from successful completion
            try {
                Project proj = projectManager.get(entity.getProjectId()).orElse(null);
                if (proj != null) {
                    TaskPlan planObj = null;
                    if (entity.getPlanJson() != null && !entity.getPlanJson().isBlank()) {
                        try {
                            planObj = new com.fasterxml.jackson.databind.ObjectMapper().readValue(entity.getPlanJson(), TaskPlan.class);
                        } catch (Exception ignored) {
                        }
                    }
                    if (planObj != null) {
                        ExecutionState state = toDomain(entity);
                        learningExtractor.extractAndStoreLearnings(state, proj, planObj, null);
                    }
                }
            } catch (Exception e) {
                // Don't fail the task if learning extraction fails
                System.err.println("Learning extraction failed: " + e.getMessage());
            }

        } catch (Exception e) {
            entity.setStatus(ExecutionStatus.FAILED);
            entity.setError(e.getMessage());
            stateRepository.save(entity);
            auditService.logTaskCompleted(taskId, false, "Error: " + e.getMessage());

            // Auto-documentation: task failed
            ExecutionState failedState = toDomain(entity);
            autoDocumentationService.onTaskFailed(project, failedState);
        }
    }

    private Mono<List<ToolResult>> executePlan(Project project, TaskPlan plan, String taskId) {
        PermissionManager.PermissionContext permContext = PermissionManager.PermissionContext.builder()
                .taskId(taskId)
                .userId("system")
                .actorType("ai")
                .hasApproval(true)
                .build();

        Map<String, ToolSpec> availableTools = toolRegistry.getAllSpecs();

        return Flux.fromIterable(plan.tasks())
                .flatMap(task -> {
                    String prompt = buildCoderPrompt(task, availableTools);

                    ChatRequest request = new ChatRequest(
                            List.of(new ChatRequest.Message("user", prompt, null, null, null)),
                            null,
                            0.2,
                            8192,
                            0.95,
                            null,
                            null,
                            false,
                            "You are an expert software engineer. Execute the task by calling the appropriate tools. Respond with tool calls only.",
                            null,
                            null,
                            null,
                            Map.of("taskId", taskId)
                    );

                    // Reactive call - no blocking here
                    return modelRouter.chatWithProfile(request, ModelProfile.CODER)
                            .flatMapIterable(response -> {
                                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                                    ToolResult errorResult = ToolResult.failure("coder", "Empty response from model router", 0);
                                    auditService.logToolExecution(task.id(), errorResult);
                                    return List.of(errorResult);
                                }

                                String content = response.choices().get(0).message().content();
                                List<ChatResponse.ToolCall> toolCalls = response.choices().get(0).message().toolCalls();

                                // Parse tool calls from content using shared parser
                                List<ToolCallParser.ToolCall> parsedCalls = ToolCallParser.parse(content);

                                if (parsedCalls.isEmpty()) {
                                    ToolResult errorResult = ToolResult.failure("coder", "No tool calls in model response: " + content, 0);
                                    auditService.logToolExecution(task.id(), errorResult);
                                    return List.of(errorResult);
                                }

                                List<ToolResult> results = new java.util.ArrayList<>();
                                boolean allSucceeded = true;

                                for (ToolCallParser.ToolCall tc : parsedCalls) {
                                    String toolName = tc.toolName();
                                    String arguments;
                                    if (tc.arguments() instanceof String) {
                                        arguments = (String) tc.arguments();
                                    } else {
                                        try {
                                            arguments = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tc.arguments());
                                        } catch (Exception e) {
                                            arguments = "{}";
                                        }
                                    }

                                    ToolResult result = toolRegistry.execute(project, toolName, arguments, permContext);
                                    auditService.logToolExecution(task.id(), result);

                                    if (!result.success()) {
                                        allSucceeded = false;
                                    }
                                    results.add(result);
                                }

                                // SECURITY: Validate all generated code before returning
                                SecurityValidationResult securityValidation = securityEnforcer.validateToolResults(results);
                                if (!securityValidation.passed()) {
                                    // Log security violations
                                    for (SecurityPolicy.SecurityViolation violation : securityValidation.violations()) {
                                        System.err.println("SECURITY VIOLATION: " + violation.message() + " in tool output");
                                        auditService.logSecurityViolation(taskId, violation.toString());
                                    }
                                    // Mark all results as failed due to security violation
                                    allSucceeded = false;
                                    for (int i = 0; i < results.size(); i++) {
                                        ToolResult r = results.get(i);
                                        if (r.success()) {
                                            results.set(i, ToolResult.failure(r.toolName(), "Security policy violation: " +
                                                    securityValidation.violations().stream()
                                                        .map(SecurityPolicy.SecurityViolation::message)
                                                        .collect(Collectors.joining("; ")), r.durationMs()));
                                        }
                                    }
                                }

                                // Track task success - success only if at least one tool call was parsed AND all succeeded
                                boolean taskSucceeded = !parsedCalls.isEmpty() && allSucceeded;
                                ExecutionStateEntity entity = stateRepository.findById(taskId).orElse(null);
                                if (entity != null) {
                                    // Could add a taskSucceeded field to entity if needed
                                }

                                return results;
                            })
                            .collectList();
                })
                .flatMapIterable(list -> list)
                .collectList();
    }

    private String buildCoderPrompt(Task task, Map<String, ToolSpec> availableTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("TASK: ").append(task.description()).append("\n\n");
        sb.append("Affected files: ").append(task.affectedFiles()).append("\n");
        sb.append("Acceptance criteria: ").append(task.acceptanceCriteria()).append("\n\n");
        sb.append("AVAILABLE TOOLS:\n");
        availableTools.forEach((name, spec) -> {
            sb.append("- ").append(name).append(": ").append(spec.description()).append("\n");
            sb.append("  Input schema: ").append(spec.inputSchema()).append("\n");
        });

        // Add security requirements to every prompt
        sb.append(securityEnforcer.enhancePromptWithSecurity(""));

        sb.append("\nRespond with tool calls to complete the task. Use the appropriate tools from the list above.");
        return sb.toString();
    }

    public ExecutionState getState(String taskId) {
        return stateRepository.findById(taskId)
                .map(this::toDomain)
                .orElse(null);
    }

    public List<ExecutionState> getAllStates() {
        return stateRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    public List<ExecutionState> getStatesByProject(String projectId) {
        return stateRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toDomain)
                .toList();
    }

    private ExecutionState toDomain(ExecutionStateEntity entity) {
        TaskPlan plan = null;
        if (entity.getPlanJson() != null && !entity.getPlanJson().isBlank()) {
            try {
                plan = new com.fasterxml.jackson.databind.ObjectMapper().readValue(entity.getPlanJson(), TaskPlan.class);
            } catch (Exception ignored) {
            }
        }

        List<ToolResult> toolResults = null;
        if (entity.getToolResultsJson() != null && !entity.getToolResultsJson().isBlank()) {
            try {
                toolResults = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(entity.getToolResultsJson(),
                                new com.fasterxml.jackson.core.type.TypeReference<List<ToolResult>>() {});
            } catch (Exception ignored) {
            }
        }

        return new ExecutionState(
                entity.getTaskId(),
                entity.getProjectId(),
                entity.getRequest(),
                entity.getRequester(),
                entity.getCreatedAt(),
                entity.getStatus(),
                entity.getError(),
                plan,
                toolResults,
                false // taskSucceeded not stored in entity yet
        );
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}