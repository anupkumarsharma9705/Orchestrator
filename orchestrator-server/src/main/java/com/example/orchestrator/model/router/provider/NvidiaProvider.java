package com.example.orchestrator.model.router.provider;

import com.example.orchestrator.model.router.ModelProvider;
import com.example.orchestrator.model.router.ModelCapabilities;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

@Component("serverNvidiaProvider")
@ConditionalOnProperty(prefix = "orchestrator.model-router.providers.nvidia", name = "enabled", havingValue = "true", matchIfMissing = false)
public class NvidiaProvider implements ModelProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final ConcurrentHashMap<String, ModelCapabilities> capabilitiesCache = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(NvidiaProvider.class);

    public NvidiaProvider(
            @Value("${orchestrator.model-router.providers.nvidia.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${orchestrator.model-router.providers.nvidia.api-key:}") String apiKey,
            @Value("${orchestrator.model-router.providers.nvidia.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = resolveApiKey(apiKey);
        this.timeoutSeconds = timeoutSeconds;
        
        // Only build WebClient if API key is available
        if (this.apiKey != null && !this.apiKey.isBlank()) {
            this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + this.apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        } else {
            this.webClient = null;
            log.warn("NVIDIA API key not configured. Provider will not be available.");
        }
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        // Check if it's an unresolved placeholder
        if (apiKey.contains("${") || apiKey.contains("NVIDIA_API_KEY")) {
            return null;
        }
        return apiKey;
    }

    @Override
    public String getProviderName() {
        return "nvidia";
    }

    @Override
    public boolean isAvailable() {
        return webClient != null && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("NVIDIA provider not available - API key not configured"));
        }
        
        NvidiaRequest nvidiaRequest = toNvidiaRequest(request);
        String requestBody;
        try {
            requestBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(nvidiaRequest);
        } catch (Exception e) {
            requestBody = "<failed to serialize request>";
        }
        log.debug("NVIDIA request: {}", requestBody);

        return webClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(nvidiaRequest)
            .exchangeToMono(response -> {
                if (response.statusCode().is4xxClientError()) {
                    return response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("NVIDIA 4xx error: status={}, body={}", response.statusCode(), body);
                            return Mono.error(new RuntimeException("NVIDIA 4xx error: " + response.statusCode() + " - " + body));
                        });
                }
                return response.bodyToMono(NvidiaResponse.class);
            })
            .map(this::toChatResponse)
            .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public Mono<String> chatStreaming(ChatRequest request) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("NVIDIA provider not available - API key not configured"));
        }
        return chat(request).map(this::serializeResponse);
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.defaultCapabilities();
    }

    private NvidiaRequest toNvidiaRequest(ChatRequest request) {
        return new NvidiaRequest(
            request.model(),
            request.messages().stream()
                .map(m -> new NvidiaMessage(m.role(), m.content()))
                .toList(),
            request.temperature(),
            request.maxTokens(),
            request.topP(),
            request.stopSequences(),
            request.stream(),
            request.tools() != null ? request.tools().stream()
                .map(t -> new NvidiaTool(t.function().name(), t.function().description(), t.function().parameters()))
                .toList() : null,
            request.responseFormat() != null ? request.responseFormat().type() : null
        );
    }

    private ChatResponse toChatResponse(NvidiaResponse response) {
        // Handle null usage with defaults
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        
        if (response.usage() != null) {
            promptTokens = response.usage().promptTokens() != null ? response.usage().promptTokens() : 0;
            completionTokens = response.usage().completionTokens() != null ? response.usage().completionTokens() : 0;
            totalTokens = response.usage().totalTokens() != null ? response.usage().totalTokens() : 0;
        }
        
        return new ChatResponse(
            response.id(),
            response.object(),
            response.created(),
            response.model(),
            response.choices().stream()
                .map(c -> new ChatResponse.Choice(
                    c.index(),
                    new ChatResponse.Message(c.message().role(), c.message().content(), null),
                    c.finishReason(),
                    null
                ))
                .toList(),
            new ChatResponse.Usage(promptTokens, completionTokens, totalTokens, null, null),
            response.systemFingerprint(),
            "nvidia",
            null
        );
    }

    private String serializeResponse(ChatResponse response) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Internal DTOs for NVIDIA API (compatible with OpenAI-compatible API)
    private record NvidiaRequest(
        String model,
        java.util.List<NvidiaMessage> messages,
        Double temperature,
        Integer maxTokens,
        Double topP,
        java.util.List<String> stop,
        Boolean stream,
        java.util.List<NvidiaTool> tools,
        String responseFormat
    ) {}

    private record NvidiaMessage(String role, String content) {}

    private record NvidiaTool(String type, String function, String description, Object parameters) {
        public NvidiaTool(String function, String description, Object parameters) {
            this("function", function, description, parameters);
        }
    }

    private record NvidiaResponse(
        String id,
        String object,
        Long created,
        String model,
        java.util.List<NvidiaChoice> choices,
        NvidiaUsage usage,
        String systemFingerprint
    ) {}

    private record NvidiaChoice(
        Integer index,
        NvidiaMessage message,
        String finishReason
    ) {}

    private record NvidiaUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
    ) {}
}