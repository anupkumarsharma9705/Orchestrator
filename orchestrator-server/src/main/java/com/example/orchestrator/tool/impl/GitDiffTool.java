package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class GitDiffTool implements Tool {

    private final ObjectMapper objectMapper;

    public GitDiffTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "gitDiff",
            "Get the git diff of the project repository",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "staged": { "type": "boolean", "description": "Show staged changes (default: false)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "isRepo": { "type": "boolean" },
                "diff": { "type": "string" }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            GitDiffInput params = objectMapper.readValue(input, GitDiffInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("gitDiff", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "git", "diff", params.staged != null && params.staged ? "--cached" : ""
            ).directory(targetPath.toFile()).redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("gitDiff", "Command timed out", System.currentTimeMillis() - start);
            }
            
            String result = objectMapper.writeValueAsString(new GitDiffOutput(true, stdout));
            return ToolResult.success("gitDiff", result, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            try {
                String result = objectMapper.writeValueAsString(new GitDiffOutput(false, "Not a git repository or git not available"));
                return ToolResult.success("gitDiff", result, System.currentTimeMillis() - start);
            } catch (Exception ex) {
                return ToolResult.failure("gitDiff", e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    private record GitDiffInput(String path, Boolean staged) {}
    private record GitDiffOutput(boolean isRepo, String diff) {}
}