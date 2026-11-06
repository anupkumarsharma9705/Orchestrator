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
public class RunMavenTestTool implements Tool {

    private final ObjectMapper objectMapper;

    public RunMavenTestTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("runMavenTest", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("runMavenTest", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            // Check for mvnw or mvnw.cmd
            Path mvnw = targetPath.resolve("mvnw");
            Path mvnwCmd = targetPath.resolve("mvnw.cmd");
            String mvnCmd = Files.exists(mvnwCmd) ? "mvnw.cmd" : (Files.exists(mvnw) ? "./mvnw" : "mvn");
            
            List<String> goals = params.goals != null && !params.goals.isEmpty() ? params.goals : List.of("test");
            String goalError = MavenGoalValidator.validate(goals);
            if (goalError != null) {
                return ToolResult.failure("runMavenTest", goalError, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 120;
            
            List<String> command = new ArrayList<>();
            command.add(mvnCmd);
            command.addAll(goals);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(targetPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            String result = objectMapper.writeValueAsString(new RunMavenTestOutput(exitCode, stdout, exitCode == 0));
            return ToolResult.success("runMavenTest", result, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("runMavenTest", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record RunMavenTestInput(String path, List<String> goals, Integer timeoutSeconds) {}
    private record RunMavenTestOutput(int exitCode, String output, boolean success) {}
}