package com.example.orchestrator.core.coder;

import java.util.List;

/**
 * Result of a coding task execution.
 */
public record CoderResult(
    boolean success,
    List<ToolCall> toolCalls,
    List<String> errors,
    String compileOutput,
    int retryCount
) {
    public static CoderResult success(List<ToolCall> toolCalls) {
        return new CoderResult(true, toolCalls, List.of(), "", 0);
    }

    public static CoderResult failure(List<String> errors) {
        return new CoderResult(false, List.of(), errors, "", 0);
    }

    public static CoderResult compileFailure(String compileOutput, List<ToolCall> toolCalls, int retryCount) {
        return new CoderResult(false, toolCalls, List.of("Compilation failed"), compileOutput, retryCount);
    }
}