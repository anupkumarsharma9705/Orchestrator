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
public class TrivyScanTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;
    /**
     * Image reference: registry host with optional port, repository path, tag
     * and digest. Anchored so a leading dash cannot smuggle Trivy flags.
     */
    private static final Pattern SAFE_TARGET_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9][a-zA-Z0-9._-]*(?::[0-9]{1,5})?"
            + "(?:/[a-zA-Z0-9][a-zA-Z0-9._-]*)*"
            + "(?::[a-zA-Z0-9][a-zA-Z0-9._-]{0,127})?"
            + "(?:@sha256:[a-fA-F0-9]{64})?$");

    public TrivyScanTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "trivyScan",
            "Run Trivy vulnerability scanner on filesystem or Docker image",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "target": { "type": "string", "description": "Target to scan: fs (filesystem) or image name (default: fs)" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 180)" }
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
                      "vulnId": { "type": "string" },
                      "severity": { "type": "string" },
                      "title": { "type": "string" },
                      "target": { "type": "string" }
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
            TrivyScanInput params = objectMapper.readValue(input, TrivyScanInput.class);

            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);

            if (!Files.exists(targetPath)) {
                return ToolResult.failure("trivyScan", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            String target = params.target != null ? params.target : "fs";
            if (!"fs".equals(target) && !SAFE_TARGET_PATTERN.matcher(target).matches()) {
                return ToolResult.failure("trivyScan", "Invalid image reference: " + target, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;

            List<String> command = new ArrayList<>();
            command.add("trivy");
            String relativePath = basePath.relativize(targetPath).toString();
            String workingDirectory;
            String scanTarget;

            if ("fs".equals(target)) {
                command.add("fs");
                command.add("--format");
                command.add("json");
                if (Files.isDirectory(targetPath)) {
                    workingDirectory = relativePath;
                    scanTarget = ".";
                } else {
                    workingDirectory = ".";
                    scanTarget = relativePath;
                }
                command.add(scanTarget);
            } else {
                // The sandbox has no network by default, so a remote image
                // cannot be pulled from inside the container. Export it from
                // the daemon into the workspace and scan the tar instead.
                command.add("image");
                command.add("--format");
                command.add("json");
                workingDirectory = ".";

                Path exported = Files.createTempFile(basePath, ".trivy-image-", ".tar");
                try {
                    SandboxResult saveResult = sandboxService.saveImageToFile(target, exported);
                    if (saveResult.exitCode() != 0) {
                        return ToolResult.failure("trivyScan", saveResult.stderr(),
                            System.currentTimeMillis() - start);
                    }
                    command.add("--input");
                    command.add(basePath.relativize(exported).toString());

                    SandboxResult sandboxResult = sandboxService.run(
                        command, basePath, workingDirectory, timeout);
                    return toResult(sandboxResult, start);
                } finally {
                    Files.deleteIfExists(exported);
                }
            }

            SandboxResult sandboxResult = sandboxService.run(
                command, basePath, workingDirectory, timeout);
            return toResult(sandboxResult, start);

        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("trivyScan", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("trivyScan", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private ToolResult toResult(SandboxResult sandboxResult, long start) throws Exception {
        String stdout = sandboxResult.stdout();
        int exitCode = sandboxResult.exitCode();
        List<TrivyVulnerability> vulnerabilities = parseVulnerabilities(stdout);

        TrivyScanOutput output = new TrivyScanOutput(
            exitCode, stdout + sandboxResult.stderr(),
            exitCode == 0 || exitCode == 1, vulnerabilities);
        String result = objectMapper.writeValueAsString(output);

        // Trivy returns non-zero when vulnerabilities are found, but that's not a tool failure.
        boolean toolSuccess = !sandboxResult.timedOut();

        return toolSuccess
            ? ToolResult.success("trivyScan", result, System.currentTimeMillis() - start)
            : ToolResult.failure("trivyScan", "Trivy failed to execute: " + stdout + sandboxResult.stderr(), System.currentTimeMillis() - start);
    }

    private List<TrivyVulnerability> parseVulnerabilities(String jsonOutput) {
        List<TrivyVulnerability> vulns = new ArrayList<>();
        try {
            var jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonOutput);
            if (jsonNode.has("Results")) {
                for (var result : jsonNode.get("Results")) {
                    String target = result.path("Target").asText("");
                    if (result.has("Vulnerabilities")) {
                        for (var vuln : result.get("Vulnerabilities")) {
                            vulns.add(new TrivyVulnerability(
                                vuln.path("VulnerabilityID").asText(""),
                                vuln.path("Severity").asText(""),
                                vuln.path("Title").asText(""),
                                target
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

    private record TrivyScanInput(String path, String target, Integer timeoutSeconds) {}
    private record TrivyScanOutput(int exitCode, String output, boolean success, List<TrivyVulnerability> vulnerabilities) {}
    private record TrivyVulnerability(String vulnId, String severity, String title, String target) {}
}
