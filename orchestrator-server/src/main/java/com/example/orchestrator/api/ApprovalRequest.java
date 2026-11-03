package com.example.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApprovalRequest(
    @JsonProperty("decision") String decision,
    @JsonProperty("approver") String approver,
    @JsonProperty("comment") String comment
) {}