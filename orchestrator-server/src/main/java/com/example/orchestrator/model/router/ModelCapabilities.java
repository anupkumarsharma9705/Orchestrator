package com.example.orchestrator.model.router;

import java.util.Set;

public record ModelCapabilities(
    int maxContextTokens,
    int maxOutputTokens,
    boolean supportsStreaming,
    boolean supportsTools,
    boolean supportsFunctionCalling,
    boolean supportsJsonMode,
    boolean supportsVision,
    boolean supportsAudio,
    Set<ModelFeature> supportedFeatures,
    double costPerInputToken,
    double costPerOutputToken,
    int maxConcurrentRequests,
    int rateLimitRpm
) {
    public boolean supports(ModelFeature feature) {
        return supportedFeatures != null && supportedFeatures.contains(feature);
    }

    public static ModelCapabilities defaultCapabilities() {
        return new ModelCapabilities(
            4096, 2048, true, false, false, false, false, false,
            Set.of(), 0.0, 0.0, 10, 60
        );
    }
}