package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * A single requirement derived from the user's request.
 * 
 * @param id Unique identifier (e.g., "R-001")
 * @param category Category of requirement (FUNCTIONAL, SECURITY, PERFORMANCE, etc.)
 * @param text Human-readable requirement description
 * @param priority Priority level
 */
public record Requirement(
    @NotBlank String id,
    @NotBlank String category,
    @NotBlank String text,
    @NotNull Priority priority
) {
    /**
     * Validates that the requirement ID follows the expected format (e.g., R-001).
     */
    @JsonIgnore
    public boolean isValidId() {
        return id != null && id.matches("^R-\\d{3,}$");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Requirement that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}