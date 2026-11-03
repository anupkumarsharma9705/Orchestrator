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
import java.util.regex.Pattern;

@Component
public class TrivyScanTool implements Tool {

    private final ObjectMapper objectMapper;
    private static final Pattern SAFE_TARGET_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+$");

    public TrivyScanTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("trivyScan", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("trivyScan", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            String target = params.target != null ? params.target : "fs";
            if (!"fs".equals(target) && !SAFE_TARGET_PATTERN.matcher(target).matches()) {
                return ToolResult.failure("trivyScan", "Invalid target (only alphanumeric, ., _, /, - allowed): " + target, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;
            
            List<String> command = new ArrayList<>();
            command.add("trivy");
            if ("fs".equals(target)) {
                command.add("fs");
                command.add("--format");
                command.add("json");
                command.add(targetPath.toString());
            } else {
                command.add("image");
                command.add("--format");
                command.add("json");
                command.add(target);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(targetPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            List<TrivyVulnerability> vulnerabilities = parseVulnerabilities(stdout);
            
            TrivyScanOutput output = new TrivyScanOutput(exitCode, stdout, exitCode == 0 || exitCode == 1, vulnerabilities);
            String result = objectMapper.writeValueAsString(output);
            
            // Trivy returns non-zero when vulnerabilities found, but that's not a tool failure
            boolean toolSuccess = finished;
            
            return toolSuccess 
                ? ToolResult.success("trivyScan", result, System.currentTimeMillis() - start)
                : ToolResult.failure("trivyScan", "Trivy failed to execute: " + stdout, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("trivyScan", e.getMessage(), System.currentTimeMillis() - start);
        }
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