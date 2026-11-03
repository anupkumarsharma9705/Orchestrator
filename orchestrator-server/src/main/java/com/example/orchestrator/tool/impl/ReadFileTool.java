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

@Component
public class ReadFileTool implements Tool {

    private static final long MAX_FILE_SIZE = 100 * 1024; // 100KB
    private final ObjectMapper objectMapper;

    public ReadFileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "readFile",
            "Read the contents of a file within the project root",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root" }
              },
              "required": ["path"]
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string" },
                "content": { "type": "string" },
                "size": { "type": "integer" }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            ReadFileInput params = objectMapper.readValue(input, ReadFileInput.class);
            
            if (params.path == null || params.path.isBlank()) {
                return ToolResult.failure("readFile", "Path is required", System.currentTimeMillis() - start);
            }
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path).normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("readFile", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                return ToolResult.failure("readFile", "File not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            long size = Files.size(targetPath);
            if (size > MAX_FILE_SIZE) {
                return ToolResult.failure("readFile", "File too large: " + size + " bytes (max " + MAX_FILE_SIZE + ")", System.currentTimeMillis() - start);
            }
            
            String content = Files.readString(targetPath);
            String output = objectMapper.writeValueAsString(new ReadFileOutput(params.path, content, size));
            
            return ToolResult.success("readFile", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("readFile", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private record ReadFileInput(String path) {}
    private record ReadFileOutput(String path, String content, long size) {}
}