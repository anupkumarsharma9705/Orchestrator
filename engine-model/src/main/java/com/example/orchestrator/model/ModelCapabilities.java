package com.example.orchestrator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Capabilities of an LLM provider/model.
 * 
 * @param provider Provider name
 * @param models List of supported model identifiers
 * @param maxContextTokens Maximum context window in tokens
 * @param maxOutputTokens Maximum output tokens
 * @param supportsStreaming Whether streaming is supported
 * @param supportsFunctionCalling Whether function calling is supported
 * @param supportsVision Whether vision/multimodal is supported
 * @param pricing Pricing per 1K tokens (prompt, completion) in USD
 */
public record ModelCapabilities(
    @NotBlank String provider,
    @NotNull List<String> models,
    int maxContextTokens,
    int maxOutputTokens,
    boolean supportsStreaming,
    boolean supportsFunctionCalling,
    boolean supportsVision,
    @NotNull Map<String, Pricing> pricing
) {
    public record Pricing(
        double promptPer1kTokens,
        double completionPer1kTokens
    ) {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelCapabilities that)) return false;
        return Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider);
    }
}