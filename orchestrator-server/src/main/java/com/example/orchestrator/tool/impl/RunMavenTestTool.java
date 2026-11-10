package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.sandbox.SandboxResult;
import com.example.orchestrator.sandbox.SandboxService;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.example.orchestrator.tool.WorkspacePathGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

@Component
public class RunMavenTestTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public RunMavenTestTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "runMavenTest",
            "Run Maven tests in the project",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root to the module (default: .)" },
                "goals": { "type": "array", "items": { "type": "string" }, "description": "Maven goals (default: [\"test\"])" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 120)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "exitCode": { "type": "integer" },
                "output": { "type": "string" },
                "success": { "type": "boolean" }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            RunMavenTestInput params = objectMapper.readValue(input, RunMavenTestInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("runMavenTest", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            List<String> goals = params.goals != null && !params.goals.isEmpty() ? params.goals : List.of("test");
            String goalError = MavenGoalValidator.validate(goals);
            if (goalError != null) {
                return ToolResult.failure("runMavenTest", goalError, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 120;

            List<String> command = new ArrayList<>();
            command.add("mvn");
            command.addAll(goals);

            String relativePath = basePath.relativize(targetPath).toString();
            SandboxResult sandboxResult = sandboxService.run(command, basePath, relativePath, timeout);
            String output = sandboxResult.stdout() + sandboxResult.stderr();
            int exitCode = sandboxResult.exitCode();
            boolean success = !sandboxResult.timedOut() && !sandboxResult.oomKilled() && exitCode == 0;

            String result = objectMapper.writeValueAsString(new RunMavenTestOutput(exitCode, output, success));
            return success
                ? ToolResult.success("runMavenTest", result, System.currentTimeMillis() - start)
                : ToolResult.failure("runMavenTest", output, System.currentTimeMillis() - start);
            
        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("runMavenTest", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("runMavenTest", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record RunMavenTestInput(String path, List<String> goals, Integer timeoutSeconds) {}
    private record RunMavenTestOutput(int exitCode, String output, boolean success) {}
}