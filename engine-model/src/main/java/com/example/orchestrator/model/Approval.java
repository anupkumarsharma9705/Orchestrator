package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;

/**
 * Approval record for a job's plan.
 * 
 * @param id Unique identifier
 * @param jobId Job identifier (unique per job)
 * @param status Approval status
 * @param approver Who approved/rejected
 * @param comment Optional comment
 * @param requestedAt When approval was requested
 * @param decidedAt When approval was decided
 * @param createdAt When record was created
 */
public record Approval(
    @NotBlank String id,
    @NotBlank String jobId,
    @NotNull ApprovalStatus status,
    String approver,
    String comment,
    @JsonFormat(shape = JsonFormat.Shape.STRING) @NotNull Instant requestedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant decidedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    public Approval {
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Approval that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}