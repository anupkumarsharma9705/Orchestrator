package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Complete requirements document generated from user request and clarification.
 * 
 * @param projectName Name of the project
 * @param version Version of this requirements document
 * @param originalRequest Original natural language request from user
 * @param clarifications List of clarification Q&A pairs
 * @param assumptions List of assumptions made
 * @param requirements List of structured requirements
 * @param refinedPrompt The refined prompt used for code generation
 * @param createdAt Timestamp when created
 * @param updatedAt Timestamp when last updated
 */
public record RequirementsDoc(
    @NotBlank String projectName,
    @NotBlank String version,
    @NotBlank String originalRequest,
    @Valid @Size(min = 1) List<Clarification> clarifications,
    @Size(min = 1) List<String> assumptions,
    @Valid @Size(min = 1) List<Requirement> requirements,
    @NotBlank String refinedPrompt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public RequirementsDoc {
        // Defensive copies
        clarifications = List.copyOf(clarifications);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
    }

    /**
     * Gets all requirement IDs.
     */
    public List<String> getRequirementIds() {
        return requirements.stream()
            .map(Requirement::id)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Checks if all requirement IDs are referenced in the plan.
     */
    public boolean areAllRequirementsCovered(List<String> planRequirementIds) {
        return getRequirementIds().stream()
            .allMatch(planRequirementIds::contains);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequirementsDoc that)) return false;
        return Objects.equals(projectName, that.projectName) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, version);
    }
}