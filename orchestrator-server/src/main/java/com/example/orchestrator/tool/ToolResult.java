package com.example.orchestrator.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ToolResult(
    @JsonProperty("toolName") String toolName,
    @JsonProperty("success") boolean success,
    @JsonProperty("output") String output,
    @JsonProperty("error") String error,
    @JsonProperty("durationMs") long durationMs,
    @JsonProperty("timestamp") Instant timestamp
) {
    public static ToolResult success(String toolName, String output, long durationMs) {
        return new ToolResult(toolName, true, output, null, durationMs, Instant.now());
    }

    public static ToolResult failure(String toolName, String error, long durationMs) {
        return new ToolResult(toolName, false, null, error, durationMs, Instant.now());
    }
}