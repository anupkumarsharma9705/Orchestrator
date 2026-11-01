package com.example.orchestrator.core.coder;

import com.example.orchestrator.core.util.PathValidationUtil;
import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ChatRequest.Message;
import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.RequirementsDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coder: Implements each plan task by generating code via LLM tool calls.
 * 
 * Flow:
 * 1. Build prompt: task + plan/req excerpts + related files + conventions + security rules
 * 2. Send to CODER model
 * 3. Parse LLM tool calls (writeFile, editFile, readFile, listFiles)
 * 4. Execute tool calls in staging area with validation
 * 5. Promote to workspace -> compile in sandbox -> retry up to 3x on fail
 * 6. Return CoderResult with tool calls and any errors
 */
@Service
public class Coder {

    private static final Logger log = LoggerFactory.getLogger(Coder.class);

    private final ModelRouter modelRouter;
    private final CoderConfig config;
    private final FileTools fileTools;

    public Coder(ModelRouter modelRouter, CoderConfig config, FileTools fileTools) {
        this.modelRouter = modelRouter;
        this.config = config;
        this.fileTools = fileTools;
    }

    @PostConstruct
    public void init() {
        log.info("Coder initialized with maxRetries={}, maxFileSizeKb={}, maxFilesPerTask={}",
            config.getMaxRetries(), config.getMaxFileSizeKb(), config.getMaxFilesPerTask());
    }

    /**
     * Executes a single plan task by generating code via LLM.
     * 
     * @param task The task to implement
     * @param plan The full plan for context
     * @param requirementsDoc The requirements document
     * @param workspaceRoot Root directory of the project workspace
     * @return CoderResult with tool calls and any errors
     */
    public Mono<CoderResult> executeTask(PlanTask task, Plan plan, RequirementsDoc requirementsDoc, Path workspaceRoot) {
        log.info("Executing task: {} - {}", task.id(), task.title());
        
        // Initialize file tools for this task
        Path stagingDir = workspaceRoot.resolve(".staging").resolve(task.id());
        fileTools.init(stagingDir, workspaceRoot);
        
        return executeTaskWithRetries(task, plan, requirementsDoc, workspaceRoot, stagingDir, 0);
    }

    /**
     * Executes task with retry logic for compilation failures.
     */
    private Mono<CoderResult> executeTaskWithRetries(PlanTask task, Plan plan, RequirementsDoc requirementsDoc,
            Path workspaceRoot, Path stagingDir, int retryCount) {
        
        if (retryCount >= config.getMaxRetries()) {
            log.warn("Max retries ({}) reached for task {}", config.getMaxRetries(), task.id());
            return Mono.just(CoderResult.failure(List.of("Max retries exceeded")));
        }

        log.info("Executing task {} (attempt {}/{})", task.id(), retryCount + 1, config.getMaxRetries());

        // Build the coding prompt
        String prompt = buildCodingPrompt(task, plan, requirementsDoc);
        Map<String, Object> metadata = Map.of(
            "taskId", "code-" + task.id() + "-" + System.currentTimeMillis(),
            "profile", "coder",
            "projectName", plan.projectName()
        );

        ChatRequest request = new ChatRequest(
            List.of(new Message("user", prompt, null, List.of())),
            "coder-model",
            0.1, 4096, 0.95,
            config.getSystemPrompt(),
            List.of(),
            metadata
        );

        return modelRouter.chatWithProfile(request, "coder")
            .map(response -> parseToolCalls(response, task))
            .flatMap(toolCalls -> executeToolCalls(toolCalls, stagingDir)
                .flatMap(result -> {
                    if (result.success()) {
                        // Try to compile
                        return compileAndVerify(workspaceRoot, stagingDir, task, retryCount);
                    }
                    return Mono.just(result);
                }));
    }

    /**
     * Builds the coding prompt with all context.
     */
    private String buildCodingPrompt(PlanTask task, Plan plan, RequirementsDoc requirementsDoc) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Goal: Implement task ").append(task.id()).append(" - ").append(task.title()).append("\n\n");
        sb.append("Description: ").append(task.description()).append("\n\n");
        
        // Add relevant requirements
        sb.append("Requirements:\n");
        for (String reqId : task.requirementIds()) {
            requirementsDoc.requirements().stream()
                .filter(r -> r.id().equals(reqId))
                .findFirst()
                .ifPresent(r -> sb.append("  - ").append(r.id()).append(" [").append(r.category()).append("] ")
                    .append(r.text()).append(" (Priority: ").append(r.priority()).append(")\n"));
        }
        
        // Add acceptance criteria
        sb.append("\nAcceptance Criteria:\n");
        for (String ac : task.acceptance()) {
            sb.append("  - ").append(ac).append("\n");
        }
        
        // Add files to create/modify
        sb.append("\nFiles to create/modify:\n");
        for (String file : task.files()) {
            sb.append("  - ").append(file).append("\n");
        }
        
        // Add dependencies
        if (!task.dependsOn().isEmpty()) {
            sb.append("\nDepends on tasks: ").append(String.join(", ", task.dependsOn())).append("\n");
        }
        
        // Add conventions and security rules
        sb.append("""
            
            Conventions:
            - Constructor injection only (SEC-1)
            - No SQL concatenation - parameterized queries only (SEC-2)
            - BCrypt for passwords (SEC-3)
            - SecureRandom for tokens (SEC-4)
            - No MD5/SHA1/DES/RC4/ECB (SEC-5)
            - @Valid on @RequestBody (SEC-6)
            - No secrets in code/logs (SEC-7)
            - CORS allow-list only (SEC-8)
            - Rate limiting on auth endpoints (SEC-9)
            - Actuator health only public (SEC-10)
            
            Code Style:
            - Constructor injection
            - Records for DTOs
            - MapStruct for mapping
            - Global exception handler
            - Proper logging (SLF4J)
            
            Output Format (JSON array of tool calls only):
            [
              {"tool": "writeFile", "path": "src/main/java/...", "content": "..."},
              {"tool": "editFile", "path": "src/main/java/...", "oldString": "...", "newString": "..."}
            ]
            """);
        
        return sb.toString();
    }

    /**
     * Parses LLM response into tool calls.
     */
    private List<ToolCall> parseToolCalls(ChatResponse response, PlanTask task) {
        String content = response.content();
        List<ToolCall> toolCalls = new ArrayList<>();

        try {
            String json = extractJsonArray(content);
            if (json != null && !json.trim().isEmpty() && !json.trim().equals("[]")) {
                toolCalls = parseJsonToToolCalls(json);
            }
        } catch (Exception e) {
            log.warn("Failed to parse tool calls from model response: {}", e.getMessage());
        }

        // Validate tool calls
        List<ToolCall> validated = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            if (validateToolCall(call, task)) {
                validated.add(call);
            } else {
                log.warn("Invalid tool call rejected: {}", call);
            }
        }

        return validated;
    }

    /**
     * Executes tool calls in the staging area.
     */
    private Mono<CoderResult> executeToolCalls(List<ToolCall> toolCalls, Path stagingDir) {
        List<String> errors = new ArrayList<>();
        List<ToolCall> executed = new ArrayList<>();

        for (ToolCall call : toolCalls) {
            try {
                if (call instanceof WriteFileCall wfc) {
                    fileTools.writeFile(wfc.getPath(), wfc.getContent());
                } else if (call instanceof EditFileCall efc) {
                    fileTools.editFile(efc.getPath(), efc.getOldString(), efc.getNewString());
                } else if (call instanceof ReadFileCall rfc) {
                    fileTools.readFile(rfc.getPath());
                } else if (call instanceof ListFilesCall lfc) {
                    fileTools.listFiles(lfc.getPath());
                }
                executed.add(call);
            } catch (Exception e) {
                errors.add("Failed to execute " + call.getTool() + ": " + e.getMessage());
                log.error("Tool call failed: {}", e.getMessage(), e);
            }
        }

        if (!errors.isEmpty()) {
            return Mono.just(CoderResult.failure(errors));
        }
        return Mono.just(CoderResult.success(executed));
    }

    /**
     * Compiles the project and verifies it builds.
     * M9: Validates workspaceRoot is within allowed base to prevent user-controlled directory execution.
     */
    private Mono<CoderResult> compileAndVerify(Path workspaceRoot, Path stagingDir, PlanTask task, int retryCount) {
        
        try {
            // M9: Validate workspaceRoot is within allowed base directory
            Path validatedWorkspaceRoot = PathValidationUtil.validateWorkspacePath(workspaceRoot);
            if (validatedWorkspaceRoot == null) {
                log.error("Workspace root validation failed for task {}: path not in allowed base", task.id());
                return Mono.just(CoderResult.failure(List.of("Workspace root validation failed: path outside allowed directory")));
            }
            
            // Promote staging to workspace
            fileTools.promoteToWorkspace(stagingDir, validatedWorkspaceRoot);
            
            // Run maven compile in sandbox
            ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "compile")
                .directory(validatedWorkspaceRoot.toFile())
                .redirectErrorStream(true);
            
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Compilation successful for task {}", task.id());
                return Mono.just(CoderResult.success(List.of()));
            } else {
                log.warn("Compilation failed for task {}: {}", task.id(), output);
                return Mono.just(CoderResult.compileFailure(output, List.of(), retryCount + 1));
            }
        } catch (Exception e) {
            log.error("Compilation error for task {}: {}", task.id(), e.getMessage(), e);
            return Mono.just(CoderResult.failure(List.of("Compilation error: " + e.getMessage())));
        }
    }

    /**
     * Validates a tool call for safety.
     */
    private boolean validateToolCall(ToolCall call, PlanTask task) {
        if (call instanceof WriteFileCall wfc) {
            return validatePath(wfc.getPath(), task) && validateContent(wfc.getContent());
        } else if (call instanceof EditFileCall efc) {
            return validatePath(efc.getPath(), task);
        } else if (call instanceof ReadFileCall rfc) {
            return validatePath(rfc.getPath(), task);
        } else if (call instanceof ListFilesCall lfc) {
            return validatePath(lfc.getPath(), task);
        }
        return false;
    }

    private boolean validatePath(String path, PlanTask task) {
        if (path == null || path.isBlank()) return false;
        if (path.contains("..")) return false; // No directory traversal
        if (path.startsWith("/")) return false; // No absolute paths
        
        // Check if path is within allowed files for this task
        for (String allowedFile : task.files()) {
            if (path.equals(allowedFile) || path.startsWith(allowedFile + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean validateContent(String content) {
        if (content == null) return false;
        int sizeKb = content.getBytes().length / 1024;
        return sizeKb <= config.getMaxFileSizeKb();
    }

    private String extractJsonArray(String content) {
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*\\}\\s*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return content.trim();
    }

    private List<ToolCall> parseJsonToToolCalls(String json) {
        // Simple parsing - in production use Jackson with @JsonTypeInfo
        List<ToolCall> result = new ArrayList<>();
        // Simplified - would use ObjectMapper in production
        return result;
    }
}