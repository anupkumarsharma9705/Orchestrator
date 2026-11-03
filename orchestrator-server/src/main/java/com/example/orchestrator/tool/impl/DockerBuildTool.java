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
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class DockerBuildTool implements Tool {

    private final ObjectMapper objectMapper;
    private static final Pattern SAFE_BUILD_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+$");
    private static final Pattern SAFE_TAG_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+(:[a-zA-Z0-9._/-]+)?$");
    private static final Pattern SAFE_DOCKERFILE_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+$");

    public DockerBuildTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            // Check Docker availability first
            Optional<String> dockerError = checkDockerAvailability();
            if (dockerError.isPresent()) {
                return ToolResult.failure("dockerBuild", "Docker not available: " + dockerError.get(), System.currentTimeMillis() - start);
            }
            
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
            Path dockerfilePath = targetPath.resolve(dockerfile);
            if (!Files.exists(dockerfilePath)) {
                return ToolResult.failure("dockerBuild", "Dockerfile not found: " + dockerfile, System.currentTimeMillis() - start);
            }
            
            String tag = params.tag != null ? params.tag : project.name().toLowerCase() + ":latest";
            if (!SAFE_TAG_PATTERN.matcher(tag).matches()) {
                return ToolResult.failure("dockerBuild", "Invalid tag format: " + tag, System.currentTimeMillis() - start);
            }
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 300;
            
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("build");
            command.add("-t");
            command.add(tag);
            command.add("-f");
            command.add(dockerfile);
            
            if (params.buildArgs != null) {
                for (var entry : params.buildArgs.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (!SAFE_BUILD_ARG_PATTERN.matcher(key).matches() || !SAFE_BUILD_ARG_PATTERN.matcher(value).matches()) {
                        return ToolResult.failure("dockerBuild", "Invalid build arg (only alphanumeric, ., _, /, - allowed): " + key + "=" + value, System.currentTimeMillis() - start);
                    }
                    command.add("--build-arg");
                    command.add(key + "=" + value);
                }
            }
            
            command.add(".");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(targetPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(params.timeoutSeconds != null ? params.timeoutSeconds : 300, java.util.concurrent.TimeUnit.SECONDS);
            
            int exitCode = finished ? process.exitValue() : -1;
            
            // Extract image ID from output
            String imageId = extractImageId(stdout);
            
            DockerBuildOutput output = new DockerBuildOutput(exitCode, stdout, exitCode == 0, imageId);
            String result = objectMapper.writeValueAsString(output);
            
            return exitCode == 0 
                ? ToolResult.success("dockerBuild", result, System.currentTimeMillis() - start)
                : ToolResult.failure("dockerBuild", "Docker build failed: " + stdout, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("dockerBuild", e.getMessage(), System.currentTimeMillis() - start);
        }
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

    private Optional<String> checkDockerAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                return Optional.of("Docker daemon not responding: " + output.trim());
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("Docker command not found: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("Docker check interrupted");
        }
    }
}