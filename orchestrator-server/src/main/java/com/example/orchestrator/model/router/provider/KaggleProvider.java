package com.example.orchestrator.model.router.provider;

import com.example.orchestrator.model.router.ModelProvider;
import com.example.orchestrator.model.router.ModelCapabilities;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component("serverKaggleProvider")
@ConditionalOnProperty(prefix = "orchestrator.model-router.providers.kaggle", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KaggleProvider implements ModelProvider {

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final int timeoutSeconds;

    public KaggleProvider(
        @Value("${orchestrator.model-router.providers.kaggle.base-url:https://www.kaggle.com/api/v1}") String baseUrl,
        @Value("${orchestrator.model-router.providers.kaggle.api-key:${KAGGLE_API_KEY}}") String apiKey,
        @Value("${orchestrator.model-router.providers.kaggle.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getProviderName() {
        return "kaggle";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        // Kaggle API implementation would go here
        // For now, return an error indicating not implemented
        return Mono.<ChatResponse>error(new UnsupportedOperationException("Kaggle provider not yet implemented"))
            .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public Mono<String> chatStreaming(ChatRequest request) {
        return Mono.error(new UnsupportedOperationException("Kaggle streaming not implemented"));
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.defaultCapabilities();
    }
}