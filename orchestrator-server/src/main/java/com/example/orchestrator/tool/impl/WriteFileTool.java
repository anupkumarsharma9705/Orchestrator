package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.example.orchestrator.tool.WorkspacePathGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
            // Validates the nearest existing ancestor, so genuinely new nested
            // package directories are allowed while symlink escapes are not.
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);
            
            if (params.content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE) {
                return ToolResult.failure("writeFile", "Content too large: " + params.content.length() + " bytes (max " + MAX_FILE_SIZE + ")", System.currentTimeMillis() - start);
            }
            
            // Create backup if file exists. Report only a workspace-relative path.
            String backupPath = null;
            if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                Path backup = targetPath.resolveSibling(targetPath.getFileName() + ".bak." + Instant.now().toEpochMilli());
                Files.copy(targetPath, backup, LinkOption.NOFOLLOW_LINKS);
                backupPath = basePath.relativize(backup).toString();
            }
            
            WorkspacePathGuard.createParentDirectories(project.rootPath(), targetPath);
            Files.writeString(targetPath, params.content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            
            long size = Files.size(targetPath);
            String output = objectMapper.writeValueAsString(new WriteFileOutput(params.path, size, backupPath));
            
            return ToolResult.success("writeFile", output, System.currentTimeMillis() - start);
            
        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("writeFile", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // Never echo exception text here: JDK I/O errors include absolute
            // host paths, which must not be exposed to the model or API caller.
            return ToolResult.failure("writeFile", "Unable to write file (" + e.getClass().getSimpleName() + ")",
                System.currentTimeMillis() - start);
        }
    }

    private record WriteFileInput(String path, String content) {}
    private record WriteFileOutput(String path, long size, String backupPath) {}
}