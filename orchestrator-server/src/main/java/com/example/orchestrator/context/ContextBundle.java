package com.example.orchestrator.context;

import java.util.List;

public record ContextBundle(
    String projectId,
    List<ContextFile> files,
    String memoryContext,
    String originalRequest
) {
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== PROJECT MEMORY (PRIORITY) ===\n");
        sb.append(memoryContext).append("\n\n");
        
        sb.append("=== RELEVANT SOURCE FILES ===\n");
        for (ContextFile file : files) {
            sb.append("--- ").append(file.path()).append(" ---\n");
            sb.append(file.content()).append("\n\n");
        }
        
        sb.append("=== USER REQUEST ===\n");
        sb.append(originalRequest);
        
        return sb.toString();
    }

    public int totalFileCount() {
        return files.size();
    }

    public long totalContentSize() {
        return files.stream().mapToLong(f -> f.content().length()).sum();
    }
}