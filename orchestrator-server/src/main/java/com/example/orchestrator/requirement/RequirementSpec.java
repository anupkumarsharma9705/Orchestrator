package com.example.orchestrator.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RequirementSpec(
    @JsonProperty("functionalRequirements") List<String> functionalRequirements,
    @JsonProperty("securityRequirements") List<String> securityRequirements,
    @JsonProperty("performanceRequirements") List<String> performanceRequirements,
    @JsonProperty("reliabilityRequirements") List<String> reliabilityRequirements,
    @JsonProperty("testingRequirements") List<String> testingRequirements,
    @JsonProperty("observabilityRequirements") List<String> observabilityRequirements,
    @JsonProperty("configurationRequirements") List<String> configurationRequirements,
    @JsonProperty("deploymentRequirements") List<String> deploymentRequirements,
    @JsonProperty("acceptanceCriteria") List<String> acceptanceCriteria,
    @JsonProperty("assumptions") List<String> assumptions,
    @JsonProperty("risks") List<String> risks
) {
    public boolean isValid() {
        return functionalRequirements != null && !functionalRequirements.isEmpty();
    }
}