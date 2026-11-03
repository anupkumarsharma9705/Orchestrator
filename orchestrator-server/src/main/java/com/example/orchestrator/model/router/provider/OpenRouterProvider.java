package com.example.orchestrator.model.router.provider;

import com.example.orchestrator.model.router.ModelProvider;
import com.example.orchestrator.model.router.ModelCapabilities;
import com.example.orchestrator.model.router.ModelFeature;
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

@Component("serverOpenRouterProvider")
@ConditionalOnProperty(prefix = "orchestrator.model-router.providers.openrouter", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OpenRouterProvider implements ModelProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final ConcurrentHashMap<String, ModelCapabilities> capabilitiesCache = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(OpenRouterProvider.class);

    public OpenRouterProvider(
        @Value("${orchestrator.model-router.providers.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
        @Value("${orchestrator.model-router.providers.openrouter.api-key:${OPENROUTER_API_KEY}}") String apiKey,
        @Value("${orchestrator.model-router.providers.openrouter.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("HTTP-Referer", "https://orchestrator.example.com")
            .defaultHeader("X-Title", "AI Orchestrator")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("${");
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        OpenRouterRequest openRouterRequest = toOpenRouterRequest(request);
        String requestBody;
        try {
            requestBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(openRouterRequest);
        } catch (Exception e) {
            requestBody = "<failed to serialize request>";
        }
        log.debug("OpenRouter request: {}", requestBody);

        return webClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(openRouterRequest)
            .exchangeToMono(response -> {
                if (response.statusCode().is4xxClientError()) {
                    return response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("OpenRouter 4xx error: status={}, body={}", response.statusCode(), body);
                            return Mono.error(new RuntimeException("OpenRouter 4xx error: " + response.statusCode() + " - " + body));
                        });
                }
                return response.bodyToMono(OpenRouterResponse.class);
            })
            .map(this::toChatResponse)
            .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public Mono<String> chatStreaming(ChatRequest request) {
        // Streaming implementation would use Server-Sent Events
        return chat(request).map(this::serializeResponse);
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.defaultCapabilities();
    }

    private OpenRouterRequest toOpenRouterRequest(ChatRequest request) {
        return new OpenRouterRequest(
            request.model(),
            request.messages().stream()
                .map(m -> new OpenRouterMessage(m.role(), m.content()))
                .toList(),
            request.temperature(),
            request.maxTokens(),
            request.topP(),
            request.stopSequences(),
            request.stream(),
            request.tools() != null ? request.tools().stream()
                .map(t -> new OpenRouterTool(t.function().name(), t.function().description(), t.function().parameters()))
                .toList() : null,
            request.responseFormat() != null ? request.responseFormat().type() : null
        );
    }

    private ChatResponse toChatResponse(OpenRouterResponse response) {
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
            response.usage() != null ? new ChatResponse.Usage(
                response.usage().promptTokens() != null ? response.usage().promptTokens() : 0,
                response.usage().completionTokens() != null ? response.usage().completionTokens() : 0,
                response.usage().totalTokens() != null ? response.usage().totalTokens() : 0,
                null, null
            ) : null,
            response.systemFingerprint(),
            "openrouter",
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

    // Internal DTOs for OpenRouter API
    private record OpenRouterRequest(
        String model,
        java.util.List<OpenRouterMessage> messages,
        Double temperature,
        Integer maxTokens,
        Double topP,
        java.util.List<String> stop,
        Boolean stream,
        java.util.List<OpenRouterTool> tools,
        String responseFormat
    ) {}

    private record OpenRouterMessage(String role, String content) {}

    private record OpenRouterTool(String type, String function, String description, Object parameters) {
        public OpenRouterTool(String function, String description, Object parameters) {
            this("function", function, description, parameters);
        }
    }

    private record OpenRouterResponse(
        String id,
        String object,
        Long created,
        String model,
        java.util.List<OpenRouterChoice> choices,
        OpenRouterUsage usage,
        String systemFingerprint
    ) {}

    private record OpenRouterChoice(
        Integer index,
        OpenRouterMessage message,
        String finishReason
    ) {}

    private record OpenRouterUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
    ) {}
}