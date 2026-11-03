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
public class DockerImageScanTool implements Tool {

    private final ObjectMapper objectMapper;

    public DockerImageScanTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "dockerImageScan",
            "Scan a Docker image for vulnerabilities using Trivy",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "image": { "type": "string", "description": "Docker image name/tag to scan" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 180)" }
              },
              "required": ["image"]
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
                      "packageName": { "type": "string" },
                      "installedVersion": { "type": "string" },
                      "fixedVersion": { "type": "string" }
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
            DockerImageScanInput params = objectMapper.readValue(input, DockerImageScanInput.class);
            
            String image = params.image;
            if (image == null || image.isBlank()) {
                return ToolResult.failure("dockerImageScan", "Image name is required", System.currentTimeMillis() - start);
            }
            
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;
            
            List<String> command = new ArrayList<>();
            command.add("trivy");
            command.add("image");
            command.add("--format");
            command.add("json");
            command.add("--exit-code");
            command.add("0"); // Don't fail on vulnerabilities
            command.add(params.image);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(params.timeoutSeconds != null ? params.timeoutSeconds : 180, java.util.concurrent.TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            List<DockerImageVulnerability> vulnerabilities = parseVulnerabilities(stdout);
            
            DockerImageScanOutput output = new DockerImageScanOutput(exitCode, stdout, finished, vulnerabilities);
            String result = objectMapper.writeValueAsString(output);
            
            return finished
                ? ToolResult.success("dockerImageScan", result, System.currentTimeMillis() - start)
                : ToolResult.failure("dockerImageScan", "Trivy image scan failed: " + stdout, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("dockerImageScan", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private List<DockerImageVulnerability> parseVulnerabilities(String jsonOutput) {
        List<DockerImageVulnerability> vulns = new ArrayList<>();
        try {
            var jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonOutput);
            if (jsonNode.has("Results")) {
                for (var result : jsonNode.get("Results")) {
                    String target = result.path("Target").asText("");
                    if (result.has("Vulnerabilities")) {
                        for (var vuln : result.get("Vulnerabilities")) {
                            vulns.add(new DockerImageVulnerability(
                                vuln.path("VulnerabilityID").asText(""),
                                vuln.path("Severity").asText(""),
                                vuln.path("Title").asText(""),
                                vuln.path("PkgName").asText(""),
                                vuln.path("InstalledVersion").asText(""),
                                vuln.path("FixedVersion").asText("")
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return new ArrayList<>();
    }

    private record DockerImageScanInput(String image, Integer timeoutSeconds) {}
    private record DockerImageScanOutput(int exitCode, String output, boolean success, List<DockerImageVulnerability> vulnerabilities) {}
    private record DockerImageVulnerability(String vulnId, String severity, String title, String packageName, String installedVersion, String fixedVersion) {}
}