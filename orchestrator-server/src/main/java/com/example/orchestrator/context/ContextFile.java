package com.example.orchestrator.context;

public record ContextFile(
    String path,
    String content,
    int score
) {
    public ContextFile(String path, String content) {
        this(path, content, 0);
    }
}