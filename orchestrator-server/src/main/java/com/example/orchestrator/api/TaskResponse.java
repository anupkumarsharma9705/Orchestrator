package com.example.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TaskResponse(
    @JsonProperty("taskId") String taskId,
    @JsonProperty("projectId") String projectId,
    @JsonProperty("request") String request,
    @JsonProperty("requester") String requester,
    @JsonProperty("status") String status,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("planSummary") String planSummary,
    @JsonProperty("error") String error
) {}