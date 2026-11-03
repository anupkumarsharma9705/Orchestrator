package com.example.orchestrator.memory;

import java.util.Map;

public record MemoryBundle(
    String projectId,
    Map<String, String> allMemories,
    Map<String, String> priorityMemories
) {
    public String getPriorityContext() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : priorityMemories.entrySet()) {
            String content = entry.getValue();
            if (content != null && !content.isBlank()) {
                sb.append("=== ").append(entry.getKey()).append(" ===\n");
                sb.append(content).append("\n\n");
            }
        }
        return sb.toString();
    }

    public String getFullContext() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : allMemories.entrySet()) {
            String content = entry.getValue();
            if (content != null && !content.isBlank()) {
                sb.append("=== ").append(entry.getKey()).append(" ===\n");
                sb.append(content).append("\n\n");
            }
        }
        return sb.toString();
    }
}