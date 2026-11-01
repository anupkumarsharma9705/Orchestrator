package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A profile for an LLM model with specific configuration.
 * 
 * @param provider Provider name (e.g., "nvidia", "openrouter", "ollama")
 * @param model Model identifier (e.g., "nvidia/nemotron-3-ultra")
 * @param temperature Sampling temperature
 * @param maxTokens Maximum tokens in response
 * @param topP Top-p sampling parameter
 * @param systemPrompt System prompt to use
 * @param enabled Whether this profile is enabled
 * @param fallbackProfiles List of fallback profile names
 */
public record ModelProfile(
    @NotBlank String provider,
    @NotBlank String model,
    double temperature,
    int maxTokens,
    double topP,
    String systemPrompt,
    boolean enabled,
    List<String> fallbackProfiles
) {
    public ModelProfile {
        fallbackProfiles = fallbackProfiles != null ? List.copyOf(fallbackProfiles) : List.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelProfile that)) return false;
        return Objects.equals(provider, that.provider) && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, model);
    }
}