package com.example.orchestrator.repair;

import com.example.orchestrator.audit.AsyncMdcContext;
import com.example.orchestrator.artifact.ArtifactStore;
import com.example.orchestrator.execution.ExecutionEngine;
import com.example.orchestrator.memory.AutoDocumentationService;
import com.example.orchestrator.model.router.ModelProfile;
import com.example.orchestrator.model.router.ModelRouter;
import com.example.orchestrator.model.router.ToolCallParser;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import com.example.orchestrator.persistence.entity.RepairSessionEntity;
import com.example.orchestrator.persistence.repo.RepairSessionRepository;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.repair.RepairLoopConfig;
import com.example.orchestrator.verification.VerificationController;
import com.example.orchestrator.verification.VerificationConfig;
import com.example.orchestrator.verification.VerificationResult;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.permission.PermissionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RepairLoopService {

    private final ExecutionEngine executionEngine;
    private final VerificationController verificationController;
    private final ArtifactStore artifactStore;
    private final RepairAttemptRepository attemptRepository;
    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;
    private final PermissionManager permissionManager;
    private final RepairSessionRepository repairSessionRepository;
    private final ObjectMapper objectMapper;
    private final AutoDocumentationService autoDocumentationService;

    private final ConcurrentHashMap<String, RepairSession> activeRepairs = new ConcurrentHashMap<>();

    public RepairLoopService(
            ExecutionEngine executionEngine,
            VerificationController verificationController,
            ArtifactStore artifactStore,
            RepairAttemptRepository attemptRepository,
            ModelRouter modelRouter,
            ToolRegistry toolRegistry,
            PermissionManager permissionManager,
            RepairSessionRepository repairSessionRepository,
            ObjectMapper objectMapper,
            AutoDocumentationService autoDocumentationService
    ) {
        this.executionEngine = executionEngine;
        this.verificationController = verificationController;
        this.artifactStore = artifactStore;
        this.attemptRepository = attemptRepository;
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.permissionManager = permissionManager;
        this.repairSessionRepository = repairSessionRepository;
        this.objectMapper = objectMapper.registerModule(new JavaTimeModule());
        this.autoDocumentationService = autoDocumentationService;
    }

    public RepairSession startRepairLoop(String taskId, Project project, VerificationConfig verificationConfig, RepairLoopConfig loopConfig) {
        String repairId = "REPAIR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        RepairSession session = new RepairSession(
                repairId,
                taskId,
                project,
                loopConfig.maxAttempts(),
                loopConfig.attemptTimeoutMinutes(),
                loopConfig.totalTimeoutMinutes(),
                Instant.now()
        );

        RepairSessionEntity entity = new RepairSessionEntity(
                repairId, taskId, project.id(),
                loopConfig.maxAttempts(),
                loopConfig.attemptTimeoutMinutes(),
                loopConfig.totalTimeoutMinutes(),
                Instant.now()
        );
        repairSessionRepository.save(entity);

        activeRepairs.put(repairId, session);

        // Start the repair loop asynchronously with MDC context propagation
        CompletableFuture.runAsync(AsyncMdcContext.wrap(() -> runRepairLoop(repairId, project, verificationConfig, loopConfig)));

        return session;
    }

    private void runRepairLoop(String repairId, Project project, VerificationConfig verificationConfig, RepairLoopConfig loopConfig) {
        RepairSession session = activeRepairs.get(repairId);
        if (session == null) return;

        long totalStartTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= loopConfig.maxAttempts(); attempt++) {
            // Check total timeout
            if (System.currentTimeMillis() - totalStartTime > loopConfig.totalTimeoutMinutes() * 60 * 1000L) {
                RepairSession updatedSession = session.markTimeout("Total repair loop timeout exceeded");
                activeRepairs.put(repairId, updatedSession);
                persistEntity(repairId, updatedSession);
                persistAttempt(repairId, attempt, null, null, session.project());
                break;
            }

            RepairSession updatedSession = session.setCurrentAttempt(attempt);
            updatedSession = updatedSession.markAttemptStarted(Instant.now());
            activeRepairs.put(repairId, updatedSession);
            session = updatedSession;
            persistEntity(repairId, session);

            // Run verification
            VerificationConfig verificationConfigCopy = verificationConfig;
            VerificationResult verificationResult = verificationController.runVerification(
                    session.project(), verificationConfig
            );

            // M3: Check for critical severity failures - stop immediately
            VerificationResult.Severity maxSeverity = getMaxSeverity(verificationResult);
            if (maxSeverity == VerificationResult.Severity.CRITICAL && !verificationResult.isOverallSuccess()) {
                RepairSession failedSession = session.markFailed("Critical severity failure detected - stopping repair loop immediately");
                activeRepairs.put(repairId, failedSession);
                persistEntity(repairId, failedSession);
                break;
            }

            // Record attempt
            RepairAttemptRecord attemptRecord = createAttemptRecord(
                    session, attempt, verificationResult, Instant.now()
            );
            persistAttempt(repairId, attempt, verificationResult, attemptRecord, session.project());

            // Check if verification passed
            if (verificationResult.isOverallSuccess()) {
                RepairSession completedSession = session.markCompleted(true);
                activeRepairs.put(repairId, completedSession);
                persistEntity(repairId, completedSession);
                break;
            }

            // Verification failed - analyze and attempt repair
            if (attempt < loopConfig.maxAttempts()) {
                RepairSession updatedSession2 = session.markAttemptFailed("Verification failed: " + verificationResult.getFailureSummary());
                activeRepairs.put(repairId, updatedSession2);
                session = updatedSession2;
                persistEntity(repairId, session);

                // M3: Adjust retry behavior based on severity
                int delayMs = calculateRetryDelay(maxSeverity);
                
                // AI-driven repair attempt
                boolean repairAttempted = attemptRepair(repairId, attempt, verificationResult, session.project()).block();

                if (!repairAttempted) {
                    RepairSession failedSession = session.markFailed("Repair attempt failed");
                    activeRepairs.put(repairId, failedSession);
                    persistEntity(repairId, failedSession);
                    break;
                }

                // Small delay before next attempt based on severity
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            } else {
                RepairSession failedSession = session.markFailed("Max attempts reached without success");
                activeRepairs.put(repairId, failedSession);
                persistEntity(repairId, failedSession);
            }
        }

        activeRepairs.remove(repairId);
    }

    /** M3: Get the maximum severity from verification result steps. */
    private VerificationResult.Severity getMaxSeverity(VerificationResult verificationResult) {
        return verificationResult.getSteps().values().stream()
                .filter(s -> !s.success())
                .map(VerificationResult.StepResult::severity)
                .max(Enum::compareTo)
                .orElse(VerificationResult.Severity.LOW);
    }

    /** M3: Calculate retry delay based on failure severity. */
    private int calculateRetryDelay(VerificationResult.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0;  // No delay for critical (but we already stop on critical)
            case HIGH -> 2000;   // 2 second delay for high severity
            case MEDIUM -> 5000; // 5 second delay for medium
            case LOW -> 10000;   // 10 second delay for low severity
        };
    }

    private void persistEntity(String repairId, RepairSession session) {
        RepairSessionEntity entity = repairSessionRepository.findById(repairId).orElse(null);
        if (entity != null) {
            entity.setCurrentAttempt(session.currentAttempt().get());
            entity.setStatus(mapStatus(session.status()));
            entity.setError(session.failureReason());
            if (session.status() == RepairSession.RepairStatus.COMPLETED_SUCCESS ||
                session.status() == RepairSession.RepairStatus.COMPLETED_FAILED ||
                session.status() == RepairSession.RepairStatus.TIMEOUT ||
                session.status() == RepairSession.RepairStatus.CANCELLED) {
                entity.setCompletedAt(Instant.now());
            }
            repairSessionRepository.save(entity);
        }
    }

    private RepairSessionEntity.Status mapStatus(RepairSession.RepairStatus status) {
        return switch (status) {
            case PENDING, ATTEMPT_IN_PROGRESS, ATTEMPT_FAILED -> RepairSessionEntity.Status.RUNNING;
            case COMPLETED_SUCCESS -> RepairSessionEntity.Status.COMPLETED;
            case COMPLETED_FAILED -> RepairSessionEntity.Status.FAILED;
            case TIMEOUT -> RepairSessionEntity.Status.TIMEOUT;
            case CANCELLED -> RepairSessionEntity.Status.CANCELLED;
        };
    }

    private Mono<Boolean> attemptRepair(String repairId, int attemptNumber, VerificationResult verificationResult, Project project) {
        // Build repair prompt from verification failures
        String repairPrompt = buildRepairPrompt(verificationResult);

        Map<String, com.example.orchestrator.tool.ToolSpec> availableTools = toolRegistry.getAllSpecs();

        ChatRequest request = new ChatRequest(
                List.of(new ChatRequest.Message("user", repairPrompt, null, null, null)),
                null,
                0.2,
                8192,
                0.95,
                null,
                null,
                false,
                "You are an expert software engineer. Analyze the verification failures and generate tool calls to fix them. Respond with tool calls only.",
                null,
                null,
                null,
                Map.of("repairId", repairId, "attemptNumber", attemptNumber)
        );

        return modelRouter.chatWithProfile(request, ModelProfile.CODER)
                .map(response -> {
                    if (response == null || response.choices() == null || response.choices().isEmpty()) {
                        System.out.println("REPAIR ATTEMPT " + attemptNumber + " failed: Empty response from model router");
                        return false;
                    }

                    String content = response.choices().get(0).message().content();

                    // Parse tool calls from content using shared parser
                    List<ToolCallParser.ToolCall> parsedCalls = ToolCallParser.parse(content);

                    if (parsedCalls.isEmpty()) {
                        System.out.println("REPAIR ATTEMPT " + attemptNumber + " failed: No tool calls in model response: " + content);
                        return false;
                    }

                    // Execute each tool call
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

                        if (!executeToolSafely(toolName, arguments, project)) {
                            System.out.println("REPAIR ATTEMPT " + attemptNumber + " failed: Tool execution failed for " + toolName);
                            return false;
                        }
                    }

                    System.out.println("REPAIR ATTEMPT " + attemptNumber + " completed successfully with " + parsedCalls.size() + " tool calls");
                    return true;
                });
    }

    private String buildRepairPrompt(VerificationResult verificationResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("REPAIR TASK: Fix the following verification failures\n\n");

        verificationResult.getSteps().forEach((name, step) -> {
            if (!step.success()) {
                sb.append("FAILED STEP: ").append(name).append("\n");
                if (step.output() != null && !step.output().isBlank()) {
                    sb.append("OUTPUT:\n").append(step.output()).append("\n");
                }
                if (step.error() != null && !step.error().isBlank()) {
                    sb.append("ERROR:\n").append(step.error()).append("\n");
                }
                sb.append("\n");
            }
        });

        sb.append("AVAILABLE TOOLS:\n");
        Map<String, com.example.orchestrator.tool.ToolSpec> availableTools = toolRegistry.getAllSpecs();
        availableTools.forEach((toolName, spec) -> {
            sb.append("- ").append(toolName).append(": ").append(spec.description()).append("\n");
            sb.append("  Input schema: ").append(spec.inputSchema()).append("\n");
        });

        sb.append("\nRespond with tool calls to fix the failures. Use the appropriate tools from the list above.");
        return sb.toString();
    }

    private boolean executeToolSafely(String toolName, String arguments, Project project) {
        try {
            PermissionManager.PermissionContext permContext = PermissionManager.PermissionContext.builder()
                    .taskId("repair")
                    .userId("system")
                    .actorType("ai")
                    .hasApproval(true)
                    .build();

            ToolResult result = toolRegistry.execute(project, toolName, arguments, permContext);

            if (result.success()) {
                System.out.println("Tool " + toolName + " executed successfully");
                return true;
            } else {
                System.err.println("Tool " + toolName + " failed: " + result.error());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Tool " + toolName + " threw exception: " + e.getMessage());
            return false;
        }
    }

    private String extractToolNameFromContent(String content) {
        if (content != null && content.matches("^[a-zA-Z][a-zA-Z0-9]*$")) {
            return content.trim();
        }
        return null;
    }

    private RepairAttemptRecord createAttemptRecord(RepairSession session, int attempt, VerificationResult result, Instant timestamp) {
        return new RepairAttemptRecord(
                java.util.UUID.randomUUID().toString().substring(0, 8),
                session.repairId(),
                attempt,
                session.taskId(),
                result.isOverallSuccess() ? "SUCCESS" : "FAILED",
                result.getFailureSummary(),
                new java.util.ArrayList<>(result.getSteps().values()),
                result.getDurationMs(),
                timestamp
        );
    }

    private void persistAttempt(String repairId, int attempt, VerificationResult result, RepairAttemptRecord record, Project project) {
        // Store in repository
        if (record != null) {
            attemptRepository.save(record);
        }

        // Auto-documentation: repair attempt
        if (record != null) {
            autoDocumentationService.onRepairAttempt(project, record);
        }

        // Store detailed artifact
        try {
            String artifactId = artifactStore.storeArtifact(
                    "repair-" + repairId,
                    "attempt-" + attempt + ".json",
                    objectMapper.writeValueAsString(record),
                    "application/json"
            );
            System.out.println("Stored attempt artifact: " + artifactId);
        } catch (Exception e) {
            System.err.println("Failed to store attempt artifact: " + e.getMessage());
        }
    }

    public RepairSession getSession(String repairId) {
        return activeRepairs.get(repairId);
    }

    public void cancelRepair(String repairId) {
        RepairSession session = activeRepairs.remove(repairId);
        if (session != null) {
            session.markCancelled();
            persistEntity(repairId, session);
        }
    }
}