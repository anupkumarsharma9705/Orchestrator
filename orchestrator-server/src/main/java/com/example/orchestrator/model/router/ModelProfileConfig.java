package com.example.orchestrator.model.router;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ModelProfileConfig(
    @JsonProperty("profile") ModelProfile profile,
    @JsonProperty("provider") String provider,
    @JsonProperty("model") String model,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("maxTokens") Integer maxTokens,
    @JsonProperty("topP") Double topP,
    @JsonProperty("topK") Integer topK,
    @JsonProperty("systemPrompt") String systemPrompt,
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("fallbackProfiles") java.util.List<ModelProfile> fallbackProfiles,
    @JsonProperty("parameters") java.util.Map<String, Object> parameters
) {
    public static ModelProfileConfig defaultFor(ModelProfile profile) {
        return switch (profile) {
            case PLANNER -> new ModelProfileConfig(profile, "openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free", 0.1, 4096, 0.9, null, 
                "You are an expert software architect. Create detailed, actionable implementation plans.", true, 
                java.util.List.of(ModelProfile.CODER, ModelProfile.GENERAL), Map.of());
            case CODER -> new ModelProfileConfig(profile, "openrouter", "cohere/north-mini-code:free", 0.2, 8192, 0.95, null,
                "You are an expert software engineer. Write clean, correct, production-ready code.", true,
                java.util.List.of(ModelProfile.REVIEWER, ModelProfile.GENERAL), Map.of());
            case REVIEWER -> new ModelProfileConfig(profile, "openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free", 0.1, 4096, 0.9, null,
                "You are a senior code reviewer. Find bugs, security issues, and suggest improvements.", true,
                java.util.List.of(ModelProfile.SECURITY_REASONING, ModelProfile.GENERAL), Map.of());
            case SECURITY_REASONING -> new ModelProfileConfig(profile, "openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free", 0.0, 4096, 1.0, null,
                "You are a security expert. Find vulnerabilities, injection flaws, and security anti-patterns.", true,
                java.util.List.of(ModelProfile.REVIEWER, ModelProfile.GENERAL), Map.of());
            case DOCUMENTATION -> new ModelProfileConfig(profile, "openrouter", "cohere/north-mini-code:free", 0.3, 4096, 0.9, null,
                "You are a technical writer. Create clear, accurate documentation.", true,
                java.util.List.of(ModelProfile.GENERAL), Map.of());
            case GENERAL -> new ModelProfileConfig(profile, "openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free", 0.5, 4096, 0.95, null,
                "You are a helpful AI assistant.", true,
                java.util.List.of(), Map.of());
        };
    }
}