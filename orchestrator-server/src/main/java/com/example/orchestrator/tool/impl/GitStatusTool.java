package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.sandbox.SandboxResult;
import com.example.orchestrator.sandbox.SandboxService;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

@Component
public class GitStatusTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public GitStatusTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
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
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("gitStatus", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            String relativePath = basePath.relativize(targetPath).toString();
            String workingDirectory = relativePath.isEmpty() ? "." : relativePath;

            // Git is executed inside the sandbox image, never on the host.
            SandboxResult sandboxResult = sandboxService.run(
                List.of("git", "status", "--porcelain", "--branch"),
                basePath, workingDirectory, 10);

            if (sandboxResult.exitCode() < 0) {
                return ToolResult.failure("gitStatus",
                    "Sandbox unavailable: " + sandboxResult.stderr(),
                    System.currentTimeMillis() - start);
            }

            String stdout = sandboxResult.stdout();
            if (sandboxResult.exitCode() != 0) {
                // Not a git repository (or git refused to run). Preserve the
                // tool's historical non-fatal contract.
                String output = objectMapper.writeValueAsString(
                    new GitStatusOutput(false, "not-a-repo", List.of()));
                return ToolResult.success("gitStatus", output, System.currentTimeMillis() - start);
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
            return ToolResult.failure("gitStatus", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record GitStatusInput(String path) {}
    private record GitStatusOutput(boolean isRepo, String branch, List<GitFileStatus> status) {}
    private record GitFileStatus(String status, String path) {}
}
