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
public class DependencyCheckTool implements Tool {

    private final ObjectMapper objectMapper;

    public DependencyCheckTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("dependencyCheck", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
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
            command.add(targetPath.toString());
            command.add("--format");
            command.add("JSON");
            command.add("--out");
            command.add(".");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(targetPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            // Parse JSON report if generated
            List<Vulnerability> vulnerabilities = new ArrayList<>();
            Path reportPath = targetPath.resolve("dependency-check-report.json");
            if (Files.exists(reportPath)) {
                vulnerabilities = parseVulnerabilities(reportPath);
            }
            
            DependencyCheckOutput output = new DependencyCheckOutput(exitCode, stdout, exitCode == 0, vulnerabilities);
            String result = objectMapper.writeValueAsString(output);
            
            boolean toolSuccess = finished;
            
            return toolSuccess 
                ? ToolResult.success("dependencyCheck", result, System.currentTimeMillis() - start)
                : ToolResult.failure("dependencyCheck", "Dependency-Check failed to execute: " + stdout, System.currentTimeMillis() - start);
            
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