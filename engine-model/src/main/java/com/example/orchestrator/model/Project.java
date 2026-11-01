package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A registered project.
 * 
 * @param id Unique identifier
 * @param name Project name
 * @param description Project description
 * @param basePackage Base Java package (e.g., com.example.library)
 * @param className Main class name (e.g., Library)
 * @param rootPath Root directory path
 * @param memoryPath Path to project memory
 * @param buildTool Build tool (MAVEN)
 * @param testCommand Test command
 * @param environment Environment variables as JSON map
 * @param createdAt When created
 * @param lastAccessedAt When last accessed
 */
public record Project(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    @NotBlank String basePackage,
    @NotBlank String className,
    @NotBlank String rootPath,
    @NotBlank String memoryPath,
    @NotBlank String buildTool,
    String testCommand,
    Map<String, String> environment,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastAccessedAt
) {
    public Project {
        createdAt = createdAt != null ? createdAt : Instant.now();
        lastAccessedAt = lastAccessedAt != null ? lastAccessedAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}