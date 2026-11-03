package com.example.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Task(
    @JsonProperty("id") String id,
    @JsonProperty("description") String description,
    @JsonProperty("affectedFiles") List<String> affectedFiles,
    @JsonProperty("dependencies") List<String> dependencies,
    @JsonProperty("risk") String risk,
    @JsonProperty("tools") List<String> tools,
    @JsonProperty("acceptanceCriteria") List<String> acceptanceCriteria,
    @JsonProperty("verificationSteps") List<String> verificationSteps
) {
    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
}