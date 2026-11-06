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
import java.util.regex.Pattern;

@Component
public class DockerImageScanTool implements Tool {

    /**
     * Registry host (with optional port), repository path, tag and digest.
     * Anchored so a leading dash cannot smuggle extra Trivy flags.
     */
    private static final Pattern SAFE_IMAGE_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9][a-zA-Z0-9._-]*(?::[0-9]{1,5})?"
            + "(?:/[a-zA-Z0-9][a-zA-Z0-9._-]*)*"
            + "(?::[a-zA-Z0-9][a-zA-Z0-9._-]{0,127})?"
            + "(?:@sha256:[a-fA-F0-9]{64})?$");

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;

    public DockerImageScanTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
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
        Path exported = null;
        try {
            DockerImageScanInput params = objectMapper.readValue(input, DockerImageScanInput.class);

            String image = params.image;
            if (image == null || image.isBlank()) {
                return ToolResult.failure("dockerImageScan", "Image name is required", System.currentTimeMillis() - start);
            }
            if (!SAFE_IMAGE_PATTERN.matcher(image).matches()) {
                return ToolResult.failure("dockerImageScan", "Invalid image reference: " + image,
                    System.currentTimeMillis() - start);
            }

            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 180;

            Path basePath = project.rootPath().toAbsolutePath().normalize();
            // Export the image from the Docker daemon into the mounted
            // workspace. The scanner then reads the tar inside the sandbox;
            // the Docker socket is never exposed to a container.
            exported = Files.createTempFile(basePath, ".trivy-image-", ".tar");
            SandboxResult saveResult = sandboxService.saveImageToFile(image, exported);
            if (saveResult.exitCode() != 0) {
                return ToolResult.failure("dockerImageScan", saveResult.stderr(),
                    System.currentTimeMillis() - start);
            }

            List<String> command = new ArrayList<>();
            command.add("trivy");
            command.add("image");
            command.add("--format");
            command.add("json");
            command.add("--exit-code");
            command.add("0"); // Don't fail on vulnerabilities
            command.add("--input");
            command.add(basePath.relativize(exported).toString());

            SandboxResult sandboxResult = sandboxService.run(command, basePath, ".", timeout);
            String stdout = sandboxResult.stdout();
            int exitCode = sandboxResult.exitCode();
            boolean finished = !sandboxResult.timedOut();

            List<DockerImageVulnerability> vulnerabilities = parseVulnerabilities(stdout);

            DockerImageScanOutput output = new DockerImageScanOutput(
                exitCode, stdout + sandboxResult.stderr(), finished, vulnerabilities);
            String result = objectMapper.writeValueAsString(output);

            return finished
                ? ToolResult.success("dockerImageScan", result, System.currentTimeMillis() - start)
                : ToolResult.failure("dockerImageScan", "Trivy image scan failed: " + stdout + sandboxResult.stderr(), System.currentTimeMillis() - start);

        } catch (Exception e) {
            return ToolResult.failure("dockerImageScan", e.getMessage(), System.currentTimeMillis() - start);
        } finally {
            if (exported != null) {
                try {
                    Files.deleteIfExists(exported);
                } catch (Exception ignored) {
                    // Best-effort cleanup; the file is inside the workspace.
                }
            }
        }
    }

    private List<DockerImageVulnerability> parseVulnerabilities(String jsonOutput) {
        List<DockerImageVulnerability> vulns = new ArrayList<>();
        try {
            var jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonOutput);
            if (jsonNode.has("Results")) {
                for (var result : jsonNode.get("Results")) {
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
        return vulns;
    }

    private record DockerImageScanInput(String image, Integer timeoutSeconds) {}
    private record DockerImageScanOutput(int exitCode, String output, boolean success, List<DockerImageVulnerability> vulnerabilities) {}
    private record DockerImageVulnerability(String vulnId, String severity, String title, String packageName, String installedVersion, String fixedVersion) {}
}
