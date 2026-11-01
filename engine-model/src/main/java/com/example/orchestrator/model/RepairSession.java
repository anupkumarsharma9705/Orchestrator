package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Objects;

/**
 * A repair session for fixing findings.
 * 
 * @param id Unique identifier
 * @param jobId Job identifier
 * @param taskId Task identifier (optional)
 * @param status Session status
 * @param configJson JSON configuration for the repair loop
 * @param startedAt When started
 * @param completedAt When completed
 */
public record RepairSession(
    @NotBlank String id,
    @NotBlank String jobId,
    String taskId,
    @NotBlank String status,
    String configJson,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt
) {
    public RepairSession {
        startedAt = startedAt != null ? startedAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepairSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}