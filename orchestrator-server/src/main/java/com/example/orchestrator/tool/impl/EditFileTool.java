package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.example.orchestrator.tool.WorkspacePathGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

@Component
public class EditFileTool implements Tool {

    private static final long MAX_FILE_SIZE = 500 * 1024;
    private final ObjectMapper objectMapper;

    public EditFileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "editFile",
            "Apply a targeted edit to an existing file (find and replace)",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root" },
                "oldText": { "type": "string", "description": "Exact text to find and replace" },
                "newText": { "type": "string", "description": "Replacement text" }
              },
              "required": ["path", "oldText", "newText"]
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string" },
                "replacements": { "type": "integer" },
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
            EditFileInput params = objectMapper.readValue(input, EditFileInput.class);
            
            if (params.path == null || params.path.isBlank()) {
                return ToolResult.failure("editFile", "Path is required", System.currentTimeMillis() - start);
            }
            if (params.oldText == null || params.oldText.isBlank()) {
                return ToolResult.failure("editFile", "oldText is required and cannot be empty", System.currentTimeMillis() - start);
            }
            if (params.newText == null) {
                return ToolResult.failure("editFile", "newText is required", System.currentTimeMillis() - start);
            }
            
            Path targetPath = WorkspacePathGuard.resolve(project.rootPath(), params.path);
            
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                return ToolResult.failure("editFile", "File not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            String content = Files.readString(targetPath);
            if (!content.contains(params.oldText)) {
                return ToolResult.failure("editFile", "oldText not found in file", System.currentTimeMillis() - start);
            }
            
            // Count occurrences
            int count = 0;
            int index = 0;
            while ((index = content.indexOf(params.oldText, index)) != -1) {
                count++;
                index += params.oldText.length();
            }
            
            // Create backup next to the target and report only a workspace-relative path.
            Path backup = targetPath.resolveSibling(targetPath.getFileName() + ".bak." + Instant.now().toEpochMilli());
            Files.copy(targetPath, backup, LinkOption.NOFOLLOW_LINKS);
            String backupPath = project.rootPath().toAbsolutePath().normalize().relativize(backup).toString();
            
            String newContent = content.replace(params.oldText, params.newText);
            
            if (newContent.getBytes().length > MAX_FILE_SIZE) {
                return ToolResult.failure("editFile", "Resulting file too large", System.currentTimeMillis() - start);
            }
            
            Files.writeString(targetPath, newContent, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            
            long size = Files.size(targetPath);
            String output = objectMapper.writeValueAsString(new EditFileOutput(params.path, count, size, backupPath));
            
            return ToolResult.success("editFile", output, System.currentTimeMillis() - start);
            
        } catch (WorkspacePathGuard.PathViolationException e) {
            return ToolResult.failure("editFile", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("editFile", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record EditFileInput(String path, String oldText, String newText) {}
    private record EditFileOutput(String path, int replacements, long size, String backupPath) {}
}