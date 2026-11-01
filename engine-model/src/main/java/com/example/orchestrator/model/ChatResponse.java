package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response from a chat completion.
 * 
 * @param id Unique response identifier
 * @param model Model used for generation
 * @param choices List of completion choices
 * @param usage Token usage statistics
 * @param provider Provider that generated the response
 * @param createdAt Timestamp of creation
 */
public record ChatResponse(
    @NotBlank String id,
    @NotBlank String model,
    @NotNull List<Choice> choices,
    @NotNull Usage usage,
    @NotBlank String provider,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    public record Choice(
        int index,
        Message message,
        String finishReason
    ) {
        public record Message(
            String role,
            String content,
            List<ChatRequest.ToolCall> toolCalls
        ) {}
    }

    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatResponse that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Gets the content of the first choice's message.
     * Convenience method for extracting response content.
     */
    public String content() {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        var firstChoice = choices.get(0);
        if (firstChoice.message() == null) {
            return "";
        }
        return firstChoice.message().content();
    }
}