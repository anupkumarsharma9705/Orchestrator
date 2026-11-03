package com.example.orchestrator.model.router.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ChatResponse(
    @JsonProperty("id") String id,
    @JsonProperty("object") String object,
    @JsonProperty("created") Long created,
    @JsonProperty("model") String model,
    @JsonProperty("choices") List<Choice> choices,
    @JsonProperty("usage") Usage usage,
    @JsonProperty("systemFingerprint") String systemFingerprint,
    @JsonProperty("provider") String provider,
    @JsonProperty("latencyMs") Long latencyMs
) {
    public record Choice(
        @JsonProperty("index") Integer index,
        @JsonProperty("message") Message message,
        @JsonProperty("finishReason") String finishReason,
        @JsonProperty("logprobs") Object logprobs
    ) {}

    public record Message(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("toolCalls") List<ToolCall> toolCalls
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

    public record Usage(
        @JsonProperty("promptTokens") Integer promptTokens,
        @JsonProperty("completionTokens") Integer completionTokens,
        @JsonProperty("totalTokens") Integer totalTokens,
        @JsonProperty("promptTokensDetails") PromptTokensDetails promptTokensDetails,
        @JsonProperty("completionTokensDetails") CompletionTokensDetails completionTokensDetails
    ) {}

    public record PromptTokensDetails(
        @JsonProperty("cachedTokens") Integer cachedTokens
    ) {}

    public record CompletionTokensDetails(
        @JsonProperty("reasoningTokens") Integer reasoningTokens
    ) {}
}