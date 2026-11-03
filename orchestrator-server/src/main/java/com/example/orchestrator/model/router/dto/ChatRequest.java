package com.example.orchestrator.model.router.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ChatRequest(
    @JsonProperty("messages") List<Message> messages,
    @JsonProperty("model") String model,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("maxTokens") Integer maxTokens,
    @JsonProperty("topP") Double topP,
    @JsonProperty("topK") Integer topK,
    @JsonProperty("stopSequences") List<String> stopSequences,
    @JsonProperty("stream") Boolean stream,
    @JsonProperty("systemPrompt") String systemPrompt,
    @JsonProperty("tools") List<Tool> tools,
    @JsonProperty("toolChoice") String toolChoice,
    @JsonProperty("responseFormat") ResponseFormat responseFormat,
    @JsonProperty("metadata") Map<String, Object> metadata
) {
    public record Message(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("name") String name,
        @JsonProperty("toolCalls") List<ToolCall> toolCalls,
        @JsonProperty("toolCallId") String toolCallId
    ) {}

    public record ToolCall(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("function") Function function
    ) {}

    public record Function(
        @JsonProperty("name") String name,
        @JsonProperty("arguments") String arguments
    ) {}

    public record Tool(
        @JsonProperty("type") String type,
        @JsonProperty("function") FunctionDefinition function
    ) {}

    public record FunctionDefinition(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("parameters") Object parameters
    ) {}

    public record ResponseFormat(
        @JsonProperty("type") String type,
        @JsonProperty("jsonSchema") Object jsonSchema
    ) {}
}