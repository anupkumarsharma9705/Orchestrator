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
public class DependencyCheckTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public DependencyCheckTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "dependencyCheck",
            "Run OWASP Dependency-Check vulnerability scan",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 300)" }
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
                "vulnerabilities": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "cve": { "type": "string" },
                      "severity": { "type": "string" },
                      "description": { "type": "string" },
                      "file": { "type": "string" }
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
            DependencyCheckInput params = objectMapper.readValue(input, DependencyCheckInput.class);

            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);

            if (!Files.exists(targetPath)) {
                return ToolResult.failure("dependencyCheck", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            // Validate targetPath is a directory and readable
            if (!Files.isDirectory(targetPath) || !Files.isReadable(targetPath)) {
                return ToolResult.failure("dependencyCheck", "Target path is not a readable directory: " + params.path, System.currentTimeMillis() - start);
            }

            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 300;

            List<String> command = new ArrayList<>();
            command.add("dependency-check");
            command.add("--scan");
            command.add(".");
            command.add("--format");
            command.add("JSON");
            command.add("--out");
            command.add(".");
            // The sandbox root filesystem is read-only; keep the NVD database
            // in the writable /tmp tmpfs instead of /opt/dependency-check/data.
            command.add("--data");
            command.add("/tmp/dependency-check-data");

            String relativePath = basePath.relativize(targetPath).toString();
            SandboxResult sandboxResult = sandboxService.run(
                command, basePath, relativePath, timeout);

            String stdout = sandboxResult.stdout() + sandboxResult.stderr();
            int exitCode = sandboxResult.exitCode();

            // Parse JSON report if generated. The workspace is bind-mounted, so
            // the report written inside the sandbox is visible on the host.
            List<Vulnerability> vulnerabilities = new ArrayList<>();
            Path reportPath = targetPath.resolve("dependency-check-report.json");
            if (Files.exists(reportPath)) {
                vulnerabilities = parseVulnerabilities(reportPath);
            }

            DependencyCheckOutput output = new DependencyCheckOutput(exitCode, stdout, exitCode == 0, vulnerabilities);
            String result = objectMapper.writeValueAsString(output);

            boolean toolSuccess = !sandboxResult.timedOut();

            return toolSuccess
                ? ToolResult.success("dependencyCheck", result, System.currentTimeMillis() - start)
                : ToolResult.failure("dependencyCheck", "Dependency-Check failed to execute: " + stdout, System.currentTimeMillis() - start);

        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("dependencyCheck", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("dependencyCheck", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private List<Vulnerability> parseVulnerabilities(Path reportPath) {
        List<Vulnerability> vulns = new ArrayList<>();
        try {
            var jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(reportPath));
            if (jsonNode.has("dependencies")) {
                for (var dep : jsonNode.get("dependencies")) {
                    String file = dep.path("fileName").asText("");
                    if (dep.has("vulnerabilities")) {
                        for (var vuln : dep.get("vulnerabilities")) {
                            vulns.add(new Vulnerability(
                                vuln.path("name").asText(""),
                                vuln.path("severity").asText(""),
                                vuln.path("description").asText(""),
                                file
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return vulns;
    }

    private record DependencyCheckInput(String path, Integer timeoutSeconds) {}
    private record DependencyCheckOutput(int exitCode, String output, boolean success, List<Vulnerability> vulnerabilities) {}
    private record Vulnerability(String cve, String severity, String description, String file) {}
}
