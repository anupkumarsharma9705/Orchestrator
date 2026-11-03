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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ListDirectoryTool implements Tool {

    private static final Set<String> EXCLUDE_DIRS = Set.of(
        ".git", "target", "build", "node_modules", "venv", ".venv", "__pycache__",
        ".idea", ".vscode", "out", "dist", "bin", "obj", "~"
    );

    private final ObjectMapper objectMapper;

    public ListDirectoryTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "listDirectory",
            "List files and directories in a given path within the project root",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "maxDepth": { "type": "integer", "description": "Maximum depth to traverse (default: 2)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "entries": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "path": { "type": "string" },
                      "isDirectory": { "type": "boolean" },
                      "size": { "type": "integer" }
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
            ListDirectoryInput params = objectMapper.readValue(input, ListDirectoryInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            // Security: ensure target is within project root
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("listDirectory", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                return ToolResult.failure("listDirectory", "Directory not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            // Make basePath effectively final for lambda
            final Path finalBasePath = basePath;
            
            int maxDepth = params.maxDepth != null ? params.maxDepth : 2;
            List<DirectoryEntry> entries = new ArrayList<>();
            
            Files.walk(targetPath, maxDepth)
                .filter(p -> !p.equals(targetPath))
                .filter(this::shouldInclude)
                .forEach(p -> {
                    try {
                        Path relative = finalBasePath.relativize(p);
                        entries.add(new DirectoryEntry(
                            p.getFileName().toString(),
                            relative.toString(),
                            Files.isDirectory(p),
                            Files.isRegularFile(p) ? Files.size(p) : 0
                        ));
                    } catch (IOException ignored) {}
                });
            
            String output = objectMapper.writeValueAsString(new ListDirectoryOutput(entries));
            return ToolResult.success("listDirectory", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("listDirectory", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean shouldInclude(Path path) {
        for (Path element : path) {
            if (EXCLUDE_DIRS.contains(element.toString())) {
                return false;
            }
        }
        return true;
    }

    record ListDirectoryInput(String path, Integer maxDepth) {}
    record ListDirectoryOutput(List<DirectoryEntry> entries) {}
    record DirectoryEntry(String name, String path, boolean isDirectory, long size) {}
}