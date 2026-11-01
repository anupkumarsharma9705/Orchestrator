package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;

/**
 * Project memory entry (learned conventions, patterns, etc.).
 * 
 * @param id Unique identifier
 * @param projectId Project identifier
 * @param category Category (CONVENTION, PATTERN, LEARNING, ERROR_PATTERN)
 * @param key Memory key
 * @param value Memory value
 * @param priority Priority for retrieval (higher = more important)
 * @param createdAt When created
 * @param updatedAt When last updated
 */
public record ProjectMemory(
    @NotBlank String id,
    @NotBlank String projectId,
    @NotBlank String category,
    @NotBlank String key,
    @NotBlank String value,
    int priority,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public ProjectMemory {
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectMemory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}