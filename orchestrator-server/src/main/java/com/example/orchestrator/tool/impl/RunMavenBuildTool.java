package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class RunMavenBuildTool implements Tool {

    private final ObjectMapper objectMapper;

    public RunMavenBuildTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("runMavenBuild", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("runMavenBuild", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            // Check for mvnw or mvnw.cmd - use absolute paths to prevent command injection
            Path mvnw = targetPath.resolve("mvnw");
            Path mvnwCmd = targetPath.resolve("mvnw.cmd");
            String mvnCmd;
            if (Files.exists(mvnwCmd)) {
                mvnCmd = mvnwCmd.toAbsolutePath().toString();
            } else if (Files.exists(mvnw)) {
                mvnCmd = mvnw.toAbsolutePath().toString();
            } else {
                mvnCmd = "mvn"; // Use system mvn (assumed to be in PATH)
            }
            
            List<String> goals = params.goals != null && !params.goals.isEmpty() ? params.goals : List.of("compile");
            String goalError = MavenGoalValidator.validate(goals);
            if (goalError != null) {
                return ToolResult.failure("runMavenBuild", goalError, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;
            boolean skipTests = params.skipTests != null && params.skipTests;
            
            List<String> command = new ArrayList<>();
            command.add(mvnCmd);
            command.addAll(goals);
            if (skipTests) {
                command.add("-DskipTests");
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(targetPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            String result = objectMapper.writeValueAsString(new RunMavenBuildOutput(exitCode, stdout, exitCode == 0));
            return ToolResult.success("runMavenBuild", result, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("runMavenBuild", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record RunMavenBuildInput(String path, List<String> goals, Integer timeoutSeconds, Boolean skipTests) {}
    private record RunMavenBuildOutput(int exitCode, String output, boolean success) {}
}