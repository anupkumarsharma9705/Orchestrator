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

import java.util.Collections;
import java.time.Duration;

@Component("serverOllamaProvider")
@ConditionalOnProperty(prefix = "orchestrator.model-router.providers.ollama", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OllamaProvider implements ModelProvider {

    private final WebClient webClient;
    private final String baseUrl;
    private final int timeoutSeconds;

    public OllamaProvider(
        @Value("${orchestrator.model-router.providers.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
        @Value("${orchestrator.model-router.providers.ollama.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        // Check if Ollama is running by trying to list models
        return true; // Assume available if configured
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        return webClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toOllamaRequest(request))
            .retrieve()
            .bodyToMono(OllamaResponse.class)
            .map(this::toChatResponse)
            .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public Mono<String> chatStreaming(ChatRequest request) {
        return chat(request).map(this::serializeResponse);
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return new ModelCapabilities(
            8192, 4096, true, false, false, false, false, false,
            Collections.emptySet(), 0.0, 0.0, 5, 30
        );
    }

    private record OllamaRequest(
        String model,
        java.util.List<OllamaMessage> messages,
        Double temperature,
        Integer maxTokens,
        Double topP,
        java.util.List<String> stop,
        Boolean stream
    ) {}

    private record OllamaMessage(String role, String content) {}

    private record OllamaResponse(
        String id,
        String object,
        Long created,
        String model,
        java.util.List<OllamaChoice> choices,
        OllamaUsage usage
    ) {}

    private record OllamaChoice(
        Integer index,
        OllamaMessage message,
        String finishReason
    ) {}

    private record OllamaUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
    ) {}

    private record OllamaRequestInternal(
        String model,
        java.util.List<OllamaMessage> messages,
        Double temperature,
        Integer maxTokens,
        Double topP,
        java.util.List<String> stop,
        Boolean stream
    ) {}

    private OllamaRequestInternal toOllamaRequest(ChatRequest request) {
        return new OllamaRequestInternal(
            request.model(),
            request.messages().stream()
                .map(m -> new OllamaMessage(m.role(), m.content()))
                .toList(),
            request.temperature(),
            request.maxTokens(),
            request.topP(),
            request.stopSequences(),
            request.stream()
        );
    }

    private ChatResponse toChatResponse(OllamaResponse response) {
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
            null,
            "ollama",
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
}