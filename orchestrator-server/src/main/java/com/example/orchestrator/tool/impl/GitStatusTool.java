package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class GitStatusTool implements Tool {

    private final ObjectMapper objectMapper;

    public GitStatusTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "gitStatus",
            "Get the git status of the project repository",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "isRepo": { "type": "boolean" },
                "branch": { "type": "string" },
                "status": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "status": { "type": "string" },
                      "path": { "type": "string" }
                    }
                  }
                }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            GitStatusInput params = objectMapper.readValue(input, GitStatusInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("gitStatus", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain", "--branch")
                .directory(targetPath.toFile())
                .redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("gitStatus", "Command timed out", System.currentTimeMillis() - start);
            }
            
            String[] lines = stdout.split("\n");
            String branch = "unknown";
            List<GitFileStatus> statuses = new ArrayList<>();
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("## ")) {
                    branch = line.substring(3).split(" ")[0];
                } else if (!line.isEmpty()) {
                    String status = line.substring(0, 2).trim();
                    String path = line.substring(3).trim();
                    statuses.add(new GitFileStatus(status, path));
                }
            }
            
            String output = objectMapper.writeValueAsString(new GitStatusOutput(true, branch, statuses));
            return ToolResult.success("gitStatus", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            // Not a git repo or git not available
            try {
                String output = objectMapper.writeValueAsString(new GitStatusOutput(false, "not-a-repo", List.of()));
                return ToolResult.success("gitStatus", output, System.currentTimeMillis() - start);
            } catch (Exception ex) {
                return ToolResult.failure("gitStatus", e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    private record GitStatusInput(String path) {}
    private record GitStatusOutput(boolean isRepo, String branch, List<GitFileStatus> status) {}
    private record GitFileStatus(String status, String path) {}
}