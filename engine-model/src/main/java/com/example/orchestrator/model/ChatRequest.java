package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request for a chat completion.
 * 
 * @param messages List of messages in the conversation
 * @param model Model identifier (e.g., "nvidia/nemotron-3-ultra")
 * @param temperature Sampling temperature (0.0-2.0)
 * @param maxTokens Maximum tokens in response
 * @param topP Top-p sampling parameter
 * @param systemPrompt System prompt to prepend
 * @param tools Available tools for function calling
 * @param metadata Additional metadata (taskId, round, etc.)
 */
public record ChatRequest(
    @NotNull @Size(min = 1) List<Message> messages,
    @NotBlank String model,
    double temperature,
    int maxTokens,
    double topP,
    String systemPrompt,
    List<Tool> tools,
    Map<String, Object> metadata
) {
    public ChatRequest {
        messages = List.copyOf(messages);
        tools = tools != null ? List.copyOf(tools) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public record Message(
        @NotBlank String role,
        @NotBlank String content,
        String name,
        List<ToolCall> toolCalls
    ) {
        public Message {
            toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        }
    }

    public record Tool(
        @NotBlank String type,
        Function function
    ) {}

    public record ToolCall(
        @NotBlank String id,
        @NotBlank String type,
        Function function
    ) {}
}