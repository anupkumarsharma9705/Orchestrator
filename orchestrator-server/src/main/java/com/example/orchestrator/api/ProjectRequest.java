package com.example.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ProjectRequest(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("rootPath") String rootPath,
    @JsonProperty("buildTool") String buildTool,
    @JsonProperty("testCommand") String testCommand,
    @JsonProperty("environment") Map<String, String> environment
) {}