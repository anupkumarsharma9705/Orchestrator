package com.example.orchestrator.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public record Project(
    String id,
    String name,
    Path rootPath,
    Path memoryPath,
    String buildTool,
    String testCommand,
    Map<String, String> environment,
    Instant registeredAt,
    Instant lastAccessedAt
) {
    public Project withLastAccessedAt(Instant lastAccessedAt) {
        return new Project(id, name, rootPath, memoryPath, buildTool, testCommand, environment, registeredAt, lastAccessedAt);
    }
}