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
import java.util.regex.Pattern;

@Component
public class SemgrepScanTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;
    private static final Pattern SAFE_CONFIG_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._/-]*$");

    public SemgrepScanTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "semgrepScan",
            "Run Semgrep security scan on the project",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "config": { "type": "string", "description": "Semgrep config (default: auto)" },
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
                "success": { "type": "boolean" },
                "findings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "ruleId": { "type": "string" },
                      "severity": { "type": "string" },
                      "message": { "type": "string" },
                      "path": { "type": "string" },
                      "line": { "type": "integer" }
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
            SemgrepScanInput params = objectMapper.readValue(input, SemgrepScanInput.class);

            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);

            if (!Files.exists(targetPath)) {
                return ToolResult.failure("semgrepScan", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            String config = params.config != null ? params.config : "auto";
            if (!SAFE_CONFIG_PATTERN.matcher(config).matches()) {
                return ToolResult.failure("semgrepScan", "Invalid config (only alphanumeric, ., _, /, - allowed): " + config, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 120;

            // Run inside the sandbox image. The workspace is mounted read-write;
            // Semgrep never executes on the server host.
            List<String> command = new ArrayList<>();
            command.add("semgrep");
            command.add("--config=" + config);
            command.add("--json");

            String relativePath = basePath.relativize(targetPath).toString();
            String workingDirectory;
            String scanTarget;
            if (Files.isDirectory(targetPath)) {
                workingDirectory = relativePath;
                scanTarget = ".";
            } else {
                workingDirectory = ".";
                scanTarget = relativePath;
            }
            command.add(scanTarget);

            SandboxResult sandboxResult = sandboxService.run(
                command, basePath, workingDirectory, timeout);
            String stdout = sandboxResult.stdout();
            int exitCode = sandboxResult.exitCode();

            // Parse JSON output for findings
            List<Finding> findings = parseFindings(stdout);

            SemgrepScanOutput output = new SemgrepScanOutput(
                exitCode, stdout + sandboxResult.stderr(), exitCode == 0, findings);
            String result = objectMapper.writeValueAsString(output);

            // Semgrep returns non-zero exit code when findings are found, but that's not a tool failure.
            // Tool failure is when semgrep itself fails to run.
            boolean toolSuccess = !sandboxResult.timedOut() && (exitCode == 0 || exitCode == 1);

            return toolSuccess
                ? ToolResult.success("semgrepScan", result, System.currentTimeMillis() - start)
                : ToolResult.failure("semgrepScan", "Semgrep failed to execute: " + stdout + sandboxResult.stderr(), System.currentTimeMillis() - start);

        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("semgrepScan", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("semgrepScan", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private List<Finding> parseFindings(String jsonOutput) {
        List<Finding> findings = new ArrayList<>();
        try {
            // Parse Semgrep JSON output
            var jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonOutput);
            if (jsonNode.has("results")) {
                for (var result : jsonNode.get("results")) {
                    findings.add(new Finding(
                        result.path("rule_id").asText(""),
                        result.path("severity").asText(""),
                        result.path("message").asText(""),
                        result.path("path").asText(""),
                        result.path("start").path("line").asInt(0)
                    ));
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return findings;
    }

    private record SemgrepScanInput(String path, String config, Integer timeoutSeconds) {}
    private record SemgrepScanOutput(int exitCode, String output, boolean success, List<Finding> findings) {}
    private record Finding(String ruleId, String severity, String message, String path, int line) {}
}
