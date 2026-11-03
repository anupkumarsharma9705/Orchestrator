package com.example.orchestrator.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToolSpec(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("riskLevel") RiskLevel riskLevel,
    @JsonProperty("inputSchema") String inputSchema,
    @JsonProperty("outputSchema") String outputSchema
) {
    public enum RiskLevel { SAFE, CONTROLLED, APPROVAL_REQUIRED, HUMAN_ONLY }
}