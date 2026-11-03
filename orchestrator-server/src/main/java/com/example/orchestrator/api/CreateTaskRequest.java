package com.example.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTaskRequest(
    @JsonProperty("projectId") String projectId,
    @JsonProperty("request") String request,
    @JsonProperty("requester") String requester
) {}