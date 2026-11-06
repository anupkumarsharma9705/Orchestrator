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
import java.util.ArrayList;
import java.util.List;

@Component
public class GitDiffTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public GitDiffTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
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
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("gitDiff", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            List<String> command = new ArrayList<>();
            command.add("git");
            command.add("diff");
            if (params.staged != null && params.staged) {
                command.add("--cached");
            }

            String relativePath = basePath.relativize(targetPath).toString();
            String workingDirectory = relativePath.isEmpty() ? "." : relativePath;

            // Git is executed inside the sandbox image, never on the host.
            SandboxResult sandboxResult = sandboxService.run(
                command, basePath, workingDirectory, 10);

            if (sandboxResult.exitCode() < 0) {
                return ToolResult.failure("gitDiff",
                    "Sandbox unavailable: " + sandboxResult.stderr(),
                    System.currentTimeMillis() - start);
            }

            if (sandboxResult.exitCode() != 0) {
                // Not a git repository (or git refused to run). Preserve the
                // tool's historical non-fatal contract.
                String result = objectMapper.writeValueAsString(
                    new GitDiffOutput(false, "Not a git repository or git not available"));
                return ToolResult.success("gitDiff", result, System.currentTimeMillis() - start);
            }

            String result = objectMapper.writeValueAsString(
                new GitDiffOutput(true, sandboxResult.stdout()));
            return ToolResult.success("gitDiff", result, System.currentTimeMillis() - start);

        } catch (Exception e) {
            return ToolResult.failure("gitDiff", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record GitDiffInput(String path, Boolean staged) {}
    private record GitDiffOutput(boolean isRepo, String diff) {}
}
