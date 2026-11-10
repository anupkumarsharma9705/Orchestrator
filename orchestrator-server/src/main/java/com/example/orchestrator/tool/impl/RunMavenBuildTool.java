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
public class RunMavenBuildTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public RunMavenBuildTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "runMavenBuild",
            "Run Maven build (compile, package, etc.) in the project",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root to the module (default: .)" },
                "goals": { "type": "array", "items": { "type": "string" }, "description": "Maven goals (default: [\"compile\"])" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 180)" },
                "skipTests": { "type": "boolean", "description": "Skip test execution (default: false)" }
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
            RunMavenBuildInput params = objectMapper.readValue(input, RunMavenBuildInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("runMavenBuild", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            List<String> goals = params.goals != null && !params.goals.isEmpty() ? params.goals : List.of("compile");
            String goalError = MavenGoalValidator.validate(goals);
            if (goalError != null) {
                return ToolResult.failure("runMavenBuild", goalError, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;
            boolean skipTests = params.skipTests != null && params.skipTests;

            List<String> command = new ArrayList<>();
            // The sandbox image provides Maven; never execute a workspace script
            // on the server host.
            command.add("mvn");
            command.addAll(goals);
            if (skipTests) {
                command.add("-DskipTests");
            }

            String relativePath = basePath.relativize(targetPath).toString();
            SandboxResult sandboxResult = sandboxService.runMaven(command, basePath, relativePath, timeout);
            String output = sandboxResult.stdout() + sandboxResult.stderr();
            int exitCode = sandboxResult.exitCode();
            boolean success = !sandboxResult.timedOut() && !sandboxResult.oomKilled() && exitCode == 0;

            String result = objectMapper.writeValueAsString(new RunMavenBuildOutput(exitCode, output, success));
            return success
                ? ToolResult.success("runMavenBuild", result, System.currentTimeMillis() - start)
                : ToolResult.failure("runMavenBuild", output, System.currentTimeMillis() - start);
            
        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("runMavenBuild", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("runMavenBuild", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record RunMavenBuildInput(String path, List<String> goals, Integer timeoutSeconds, Boolean skipTests) {}
    private record RunMavenBuildOutput(int exitCode, String output, boolean success) {}
}