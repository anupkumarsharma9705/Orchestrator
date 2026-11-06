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
import java.util.regex.Pattern;

@Component
public class DockerBuildTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SandboxService sandboxService;
    private static final Pattern SAFE_BUILD_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+$");
    private static final Pattern SAFE_TAG_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+(:[a-zA-Z0-9._/-]+)?$");
    private static final Pattern SAFE_DOCKERFILE_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    public DockerBuildTool(ObjectMapper objectMapper, SandboxService sandboxService) {
        this.objectMapper = objectMapper;
        this.sandboxService = sandboxService;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "dockerBuild",
            "Build a Docker image from a Dockerfile in the project",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root containing Dockerfile (default: .)" },
                "tag": { "type": "string", "description": "Image tag (default: project-name:latest)" },
                "dockerfile": { "type": "string", "description": "Dockerfile name (default: Dockerfile)" },
                "buildArgs": { "type": "object", "description": "Build arguments as key-value pairs" },
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
                "imageId": { "type": "string" }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            DockerBuildInput params = objectMapper.readValue(input, DockerBuildInput.class);

            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();

            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("dockerBuild", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }

            if (!Files.exists(targetPath)) {
                return ToolResult.failure("dockerBuild", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }

            // Check for Dockerfile
            String dockerfile = params.dockerfile != null ? params.dockerfile : "Dockerfile";
            if (!SAFE_DOCKERFILE_PATTERN.matcher(dockerfile).matches()) {
                return ToolResult.failure("dockerBuild", "Invalid dockerfile name: " + dockerfile, System.currentTimeMillis() - start);
            }
            Path dockerfilePath = targetPath.resolve(dockerfile).normalize();
            if (!dockerfilePath.startsWith(targetPath)) {
                return ToolResult.failure("dockerBuild", "Dockerfile path traversal attempt denied", System.currentTimeMillis() - start);
            }
            if (!Files.isRegularFile(dockerfilePath)) {
                return ToolResult.failure("dockerBuild", "Dockerfile not found: " + dockerfile, System.currentTimeMillis() - start);
            }

            String tag = params.tag != null ? params.tag : defaultTag(project.name());
            if (!SAFE_TAG_PATTERN.matcher(tag).matches()) {
                return ToolResult.failure("dockerBuild", "Invalid tag format: " + tag, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 300;

            if (params.buildArgs != null) {
                for (var entry : params.buildArgs.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key == null || value == null
                            || !SAFE_BUILD_ARG_PATTERN.matcher(key).matches()
                            || !SAFE_BUILD_ARG_PATTERN.matcher(value).matches()) {
                        return ToolResult.failure("dockerBuild", "Invalid build arg (only alphanumeric, ., _, /, - allowed): " + key + "=" + value, System.currentTimeMillis() - start);
                    }
                }
            }

            // Build through the Docker daemon API. This never shells out to the
            // docker CLI and never mounts the Docker socket into a container.
            SandboxResult sandboxResult = sandboxService.buildImage(
                targetPath, dockerfile, tag, params.buildArgs, timeout);

            String output = sandboxResult.stdout() + sandboxResult.stderr();
            int exitCode = sandboxResult.exitCode();

            // Extract image ID from output
            String imageId = extractImageId(output);

            DockerBuildOutput result = new DockerBuildOutput(
                exitCode, output, exitCode == 0 && !sandboxResult.timedOut(), imageId);
            String json = objectMapper.writeValueAsString(result);

            return exitCode == 0 && !sandboxResult.timedOut()
                ? ToolResult.success("dockerBuild", json, System.currentTimeMillis() - start)
                : ToolResult.failure("dockerBuild", "Docker build failed: " + output, System.currentTimeMillis() - start);

        } catch (Exception e) {
            return ToolResult.failure("dockerBuild", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String defaultTag(String projectName) {
        String base = projectName == null ? "project" : projectName.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        if (base.isBlank()) {
            base = "project";
        }
        return base + ":latest";
    }

    private String extractImageId(String output) {
        // Extract image ID from docker build output
        for (String line : output.split("\n")) {
            if (line.contains("Successfully built")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    return parts[2];
                }
            }
            if (line.contains("sha256:")) {
                int idx = line.indexOf("sha256:");
                if (idx >= 0) {
                    return line.substring(idx).split("\\s+")[0];
                }
            }
        }
        return "";
    }

    private record DockerBuildInput(String path, String tag, String dockerfile, java.util.Map<String, String> buildArgs, Integer timeoutSeconds) {}
    private record DockerBuildOutput(int exitCode, String output, boolean success, String imageId) {}
}
