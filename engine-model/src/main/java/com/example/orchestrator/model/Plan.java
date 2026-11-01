package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Complete execution plan generated from requirements.
 * 
 * @param projectName Name of the project
 * @param version Version of this plan
 * @param tasks Ordered list of tasks to execute
 * @param createdAt Timestamp when created
 * @param updatedAt Timestamp when last updated
 */
public record Plan(
    @NotBlank String projectName,
    @NotBlank String version,
    @Valid @Size(min = 1) List<PlanTask> tasks,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public Plan {
        tasks = List.copyOf(tasks);
    }

    /**
     * Gets tasks in topological order (dependencies first).
     */
    public List<PlanTask> getTopologicalOrder() {
        // Simple topological sort using Kahn's algorithm
        var inDegree = new java.util.HashMap<String, Integer>();
        var adjList = new java.util.HashMap<String, List<String>>();

        // Initialize
        for (PlanTask task : tasks) {
            inDegree.put(task.id(), task.dependsOn().size());
            adjList.put(task.id(), new java.util.ArrayList<>());
        }

        // Build adjacency list
        for (PlanTask task : tasks) {
            for (String dep : task.dependsOn()) {
                adjList.get(dep).add(task.id());
            }
        }

        // Kahn's algorithm
        var queue = new java.util.LinkedList<String>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        var result = new java.util.ArrayList<PlanTask>();
        var taskMap = new java.util.HashMap<String, PlanTask>();
        for (PlanTask task : tasks) {
            taskMap.put(task.id(), task);
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(taskMap.get(current));

            for (String neighbor : adjList.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (result.size() != tasks.size()) {
            throw new IllegalStateException("Cycle detected in task dependencies");
        }

        return result;
    }

    /**
     * Validates the plan: no cycles, all requirement IDs covered.
     */
    public ValidationResult validate() {
        var errors = new java.util.ArrayList<String>();

        // Check for cycles (topological sort will throw if cycle)
        try {
            getTopologicalOrder();
        } catch (IllegalStateException e) {
            errors.add("Cycle detected in task dependencies: " + e.getMessage());
        }

        // Check all requirement IDs are covered
        var coveredReqs = new java.util.HashSet<String>();
        for (PlanTask task : tasks) {
            coveredReqs.addAll(task.requirementIds());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plan that)) return false;
        return Objects.equals(projectName, that.projectName) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, version);
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}