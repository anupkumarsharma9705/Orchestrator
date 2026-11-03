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
import java.time.Instant;
import java.util.regex.Pattern;

@Component
public class WriteFileTool implements Tool {

    private static final long MAX_FILE_SIZE = 500 * 1024; // 500KB
    private final ObjectMapper objectMapper;
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]+$");

    public WriteFileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "writeFile",
            "Write content to a file within the project root (creates parent directories)",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root" },
                "content": { "type": "string", "description": "File content to write" }
              },
              "required": ["path", "content"]
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string" },
                "size": { "type": "integer" },
                "backupPath": { "type": "string" }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            WriteFileInput params = objectMapper.readValue(input, WriteFileInput.class);
            
            if (params.path == null || params.path.isBlank()) {
                return ToolResult.failure("writeFile", "Path is required", System.currentTimeMillis() - start);
            }
            if (params.content == null) {
                return ToolResult.failure("writeFile", "Content is required", System.currentTimeMillis() - start);
            }
            
            // Validate path contains only safe characters
            if (!SAFE_PATH_PATTERN.matcher(params.path).matches()) {
                return ToolResult.failure("writeFile", "Invalid path (only alphanumeric, ., _, /, - allowed): " + params.path, System.currentTimeMillis() - start);
            }
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path).normalize();
            
            // Resolve symlinks to prevent bypass via symlink
            Path realBasePath;
            Path realTargetPath;
            try {
                realBasePath = basePath.toRealPath();
                realTargetPath = targetPath.toRealPath();
            } catch (IOException e) {
                // If target doesn't exist yet, resolve parent and check
                Path parentPath = targetPath.getParent();
                if (parentPath != null) {
                    try {
                        realBasePath = basePath.toRealPath();
                        realTargetPath = parentPath.toRealPath().resolve(targetPath.getFileName());
                    } catch (IOException ex) {
                        return ToolResult.failure("writeFile", "Invalid path: " + ex.getMessage(), System.currentTimeMillis() - start);
                    }
                } else {
                    return ToolResult.failure("writeFile", "Invalid path: no parent directory", System.currentTimeMillis() - start);
                }
            }
            
            if (!realTargetPath.startsWith(realBasePath)) {
                return ToolResult.failure("writeFile", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (params.content.getBytes().length > MAX_FILE_SIZE) {
                return ToolResult.failure("writeFile", "Content too large: " + params.content.length() + " bytes (max " + MAX_FILE_SIZE + ")", System.currentTimeMillis() - start);
            }
            
            // Create backup if file exists
            String backupPath = null;
            if (Files.exists(targetPath) && Files.isRegularFile(targetPath)) {
                backupPath = targetPath.toString() + ".bak." + Instant.now().toEpochMilli();
                Files.copy(targetPath, Path.of(backupPath));
            }
            
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, params.content);
            
            long size = Files.size(targetPath);
            String output = objectMapper.writeValueAsString(new WriteFileOutput(params.path, size, backupPath));
            
            return ToolResult.success("writeFile", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("writeFile", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record WriteFileInput(String path, String content) {}
    private record WriteFileOutput(String path, long size, String backupPath) {}
}