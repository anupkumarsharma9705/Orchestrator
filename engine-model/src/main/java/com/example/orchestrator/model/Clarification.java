package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single clarification question and answer pair.
 * 
 * @param id Unique identifier (e.g., "Q-001")
 * @param question The question text
 * @param defaultAnswer Default answer (can be empty)
 * @param userAnswer User's answer (null if not answered yet)
 * @param type Question type: SELECT, INPUT, BOOLEAN
 * @param options Available options for SELECT type
 */
public record Clarification(
    @NotBlank String id,
    @NotBlank String question,
    String defaultAnswer,
    String userAnswer,
    @NotNull ClarificationType type,
    List<String> options
) {
    public Clarification {
        options = options != null ? List.copyOf(options) : List.of();
    }

    /**
     * Gets the effective answer (user answer or default).
     */
    @JsonIgnore
    public String getEffectiveAnswer() {
        return userAnswer != null && !userAnswer.isBlank() ? userAnswer : defaultAnswer;
    }

    /**
     * Checks if the clarification has been answered.
     */
    @JsonIgnore
    public boolean isAnswered() {
        return userAnswer != null && !userAnswer.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Clarification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}