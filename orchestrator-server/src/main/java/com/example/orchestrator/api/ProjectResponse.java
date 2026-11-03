package com.example.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProjectResponse(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("rootPath") String rootPath,
    @JsonProperty("memoryPath") String memoryPath,
    @JsonProperty("buildTool") String buildTool,
    @JsonProperty("testCommand") String testCommand,
    @JsonProperty("registeredAt") Instant registeredAt,
    @JsonProperty("lastAccessedAt") Instant lastAccessedAt
) {}