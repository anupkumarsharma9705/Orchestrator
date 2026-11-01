package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;

/**
 * Audit trail entry for approval actions.
 * 
 * @param id Unique identifier
 * @param approvalId Approval identifier
 * @param action Action performed
 * @param actor Who performed the action (user, system, api)
 * @param comment Optional comment
 * @param timestamp When the action occurred
 */
public record ApprovalAudit(
    @NotBlank String id,
    @NotBlank String approvalId,
    @NotNull AuditAction action,
    @NotBlank String actor,
    String comment,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp
) {
    public ApprovalAudit {
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalAudit that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}