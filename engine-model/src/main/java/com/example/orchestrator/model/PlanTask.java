package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single task in the execution plan.
 * 
 * @param id Unique identifier (e.g., "T-001")
 * @param title Human-readable task title
 * @param description Detailed description of what this task does
 * @param files List of file paths this task will create/modify (relative paths)
 * @param dependsOn List of task IDs this task depends on
 * @param requirementIds List of requirement IDs this task satisfies
 * @param acceptance List of acceptance criteria for this task
 * @param status Current status of the task
 * @param createdAt Timestamp when created
 * @param updatedAt Timestamp when last updated
 */
public record PlanTask(
    @NotBlank String id,
    @NotBlank String title,
    @NotBlank String description,
    @Size(min = 1) List<String> files,
    List<String> dependsOn,
    @Size(min = 1) List<String> requirementIds,
    @Size(min = 1) List<String> acceptance,
    @NotNull TaskStatus status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public PlanTask {
        files = List.copyOf(files);
        dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
        requirementIds = List.copyOf(requirementIds);
        acceptance = List.copyOf(acceptance);
    }

    /**
     * Checks if all dependencies are satisfied (completed).
     */
    public boolean areDependenciesSatisfied(List<PlanTask> allTasks) {
        return dependsOn.stream()
            .allMatch(depId -> allTasks.stream()
                .filter(t -> t.id().equals(depId))
                .findFirst()
                .map(PlanTask::status)
                .orElse(TaskStatus.PENDING) == TaskStatus.COMPLETED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanTask that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}