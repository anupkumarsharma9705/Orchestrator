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
import java.util.regex.Pattern;

@Component
public class SearchFilesTool implements Tool {

    private static final Set<String> EXCLUDE_DIRS = Set.of(
        ".git", "target", "build", "node_modules", "venv", ".venv", "__pycache__",
        ".idea", ".vscode", "out", "dist", "bin", "obj", "~"
    );

    private final ObjectMapper objectMapper;

    public SearchFilesTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "searchFiles",
            "Search for files matching a pattern or containing text within the project root",
            ToolSpec.RiskLevel.SAFE,
            """
            {
              "type": "object",
              "properties": {
                "pattern": { "type": "string", "description": "Glob pattern or regex to match file names" },
                "contentPattern": { "type": "string", "description": "Regex pattern to search within file contents" },
                "path": { "type": "string", "description": "Relative path from project root to search in (default: .)" },
                "maxResults": { "type": "integer", "description": "Maximum number of results (default: 50)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "matches": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "path": { "type": "string" },
                      "lineNumber": { "type": "integer" },
                      "line": { "type": "string" }
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
            SearchInput params = objectMapper.readValue(input, SearchInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path searchPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!searchPath.startsWith(basePath)) {
                return ToolResult.failure("searchFiles", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(searchPath)) {
                return ToolResult.failure("searchFiles", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            Pattern namePattern = params.pattern != null ? Pattern.compile(globToRegex(params.pattern)) : null;
            Pattern contentPattern = params.contentPattern != null ? Pattern.compile(params.contentPattern) : null;
            int maxResults = params.maxResults != null ? params.maxResults : 50;
            
            List<SearchMatch> matches = new ArrayList<>();
            
            Files.walk(searchPath)
                .filter(Files::isRegularFile)
                .filter(p -> shouldInclude(p, basePath))
                .filter(p -> namePattern == null || namePattern.matcher(basePath.relativize(p).toString()).matches())
                .limit(maxResults * 2)
                .forEach(path -> {
                    try {
                        if (contentPattern != null) {
                            Files.lines(path).forEach(line -> {
                                if (contentPattern.matcher(line).find() && matches.size() < maxResults) {
                                    // Find line number
                                    try {
                                        int lineNum = findLineNumber(path, line);
                                        matches.add(new SearchMatch(basePath.relativize(path).toString(), lineNum, line));
                                    } catch (IOException ignored) {}
                                }
                            });
                        } else if (matches.size() < maxResults) {
                            matches.add(new SearchMatch(basePath.relativize(path).toString(), 0, ""));
                        }
                    } catch (IOException ignored) {}
                });
            
            String output = objectMapper.writeValueAsString(new SearchOutput(matches));
            return ToolResult.success("searchFiles", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("searchFiles", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean shouldInclude(Path path, Path basePath) {
        Path relative = basePath.relativize(path);
        for (Path element : relative) {
            if (EXCLUDE_DIRS.contains(element.toString())) {
                return false;
            }
        }
        return true;
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '{', '}', ',', '[', ']', '(', ')', '|', '^', '$', '+', '\\' -> regex.append("\\").append(c);
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }

    private int findLineNumber(Path path, String targetLine) throws IOException {
        int lineNum = 0;
        for (String line : Files.readAllLines(path)) {
            lineNum++;
            if (line.equals(targetLine)) {
                return lineNum;
            }
        }
        return lineNum;
    }

    private record SearchInput(String pattern, String contentPattern, String path, Integer maxResults) {}
    private record SearchOutput(List<SearchMatch> matches) {}
    private record SearchMatch(String path, int lineNumber, String line) {}
}