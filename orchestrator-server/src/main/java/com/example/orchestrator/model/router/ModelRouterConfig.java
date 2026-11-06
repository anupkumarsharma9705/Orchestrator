package com.example.orchestrator.model.router;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "orchestrator.model-router")
public record ModelRouterConfig(
    @JsonProperty("defaultProvider") String defaultProvider,
    @JsonProperty("providers") Map<String, ProviderConfig> providers,
    @JsonProperty("profiles") Map<ModelProfile, ModelProfileConfig> profiles,
    @JsonProperty("circuitBreaker") CircuitBreakerConfig circuitBreaker,
    @JsonProperty("costTracking") CostTrackingConfig costTracking,
    @JsonProperty("defaultProfile") ModelProfile defaultProfile,
    @JsonProperty("timeoutSeconds") Integer timeoutSeconds,
    @JsonProperty("maxRetries") Integer maxRetries,
    @JsonProperty("retryDelayMs") Integer retryDelayMs
) {
    public record ProviderConfig(
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("baseUrl") String baseUrl,
        @JsonProperty("apiKey") String apiKey,
        @JsonProperty("apiKeyEnv") String apiKeyEnv,
        @JsonProperty("models") Map<String, String> models,
        @JsonProperty("rateLimitRpm") Integer rateLimitRpm,
        @JsonProperty("timeoutSeconds") Integer timeoutSeconds,
        @JsonProperty("supportsStreaming") Boolean supportsStreaming
    ) {}

    public record CircuitBreakerConfig(
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("failureThreshold") Integer failureThreshold,
        @JsonProperty("timeoutSeconds") Integer timeoutSeconds,
        @JsonProperty("halfOpenRequests") Integer halfOpenRequests
    ) {}

    public record CostTrackingConfig(
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("budgetPerTaskUsd") Double budgetPerTaskUsd,
        @JsonProperty("budgetPerDayUsd") Double budgetPerDayUsd,
        @JsonProperty("alertThresholdPercent") Double alertThresholdPercent
    ) {}

    public static ModelRouterConfig defaultConfig() {
        return new ModelRouterConfig(
            "openrouter",
            Map.of(
                "openrouter", new ProviderConfig(
                    true,
                    "https://openrouter.ai/api/v1",
                    null,
                    "OPENROUTER_API_KEY",
                    Map.of(
                        "planner", "nvidia/nemotron-3-ultra-550b-a55b:free",
                        "coder", "cohere/north-mini-code:free",
                        "reviewer", "nvidia/nemotron-3-ultra-550b-a55b:free",
                        "security", "nvidia/nemotron-3-ultra-550b-a55b:free",
                        "docs", "cohere/north-mini-code:free",
                        "general", "nvidia/nemotron-3-ultra-550b-a55b:free"
                    ),
                    60, 60, true
                ),
                "ollama", new ProviderConfig(
                    false,
                    "http://localhost:11434/v1",
                    null,
                    null,
                    Map.of("codellama", "codellama:13b", "llama3", "llama3:8b"),
                    30, 120, true
                )
            ),
            Map.ofEntries(
                java.util.Map.entry(ModelProfile.PLANNER, ModelProfileConfig.defaultFor(ModelProfile.PLANNER)),
                java.util.Map.entry(ModelProfile.CODER, ModelProfileConfig.defaultFor(ModelProfile.CODER)),
                java.util.Map.entry(ModelProfile.REVIEWER, ModelProfileConfig.defaultFor(ModelProfile.REVIEWER)),
                java.util.Map.entry(ModelProfile.SECURITY_REASONING, ModelProfileConfig.defaultFor(ModelProfile.SECURITY_REASONING)),
                java.util.Map.entry(ModelProfile.DOCUMENTATION, ModelProfileConfig.defaultFor(ModelProfile.DOCUMENTATION)),
                java.util.Map.entry(ModelProfile.GENERAL, ModelProfileConfig.defaultFor(ModelProfile.GENERAL))
            ),
            new CircuitBreakerConfig(true, 5, 30, 3),
            new CostTrackingConfig(true, 0.50, 10.0, 80.0),
            ModelProfile.GENERAL,
            60, 3, 1000
        );
    }
}