package com.example.orchestrator.llm.provider;

import com.example.orchestrator.config.EgressFilterConfig;
import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.llm.CostTracker;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ModelCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OllamaProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final String PROVIDER_NAME = "ollama";

    private final WebClient webClient;
    private final String baseUrl;
    private final CostTracker costTracker;

    public OllamaProvider(
            @Value("${orchestrator.model-router.providers.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
            CostTracker costTracker,
            EgressFilterConfig filteredWebClientBuilder) {
        this.baseUrl = baseUrl;
        this.costTracker = costTracker;
        this.webClient = filteredWebClientBuilder.filteredWebClientBuilder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // Check if Ollama is reachable
        try {
            return webClient.get()
                    .uri("/models")
                    .retrieve()
                    .toBodilessEntity()
                    .blockOptional(Duration.ofSeconds(5))
                    .isPresent();
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("Ollama not available at " + baseUrl));
        }

        log.debug("Sending chat request to Ollama: model={}, messages={}", request.model(), request.messages().size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(request))
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .map(this::toChatResponse)
                .doOnSuccess(response -> {
                    // Local models have zero cost
                    costTracker.recordUsage(
                            request.metadata().getOrDefault("taskId", "unknown").toString(),
                            request.model(),
                            PROVIDER_NAME,
                            response.usage().promptTokens(),
                            response.usage().completionTokens()
                    );
                    log.debug("Ollama response received: id={}, tokens={}", response.id(), response.usage().totalTokens());
                })
                .doOnError(error -> log.error("Ollama chat error: {}", error.getMessage()))
                .timeout(Duration.ofSeconds(120)); // Longer timeout for local models
    }

    private Object buildRequestBody(ChatRequest request) {
        return Map.of(
                "model", request.model(),
                "messages", request.messages().stream()
                        .map(m -> Map.of(
                                "role", m.role(),
                                "content", m.content(),
                                "tool_calls", m.toolCalls() != null ? m.toolCalls() : List.of()
                        ))
                        .collect(Collectors.toList()),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "top_p", request.topP(),
                "stream", false
        );
    }

    private ChatResponse toChatResponse(OllamaResponse ollamaResponse) {
        var firstChoice = ollamaResponse.choices().get(0);
        var usage = new ChatResponse.Usage(
                ollamaResponse.usage().promptTokens(),
                ollamaResponse.usage().completionTokens(),
                ollamaResponse.usage().totalTokens()
        );
        var choice = new ChatResponse.Choice(
                0,
                new ChatResponse.Choice.Message(
                        firstChoice.message().role(),
                        firstChoice.message().content(),
                        List.of()
                ),
                firstChoice.finishReason()
        );

        return new ChatResponse(
                ollamaResponse.id(),
                ollamaResponse.model(),
                List.of(choice),
                usage,
                PROVIDER_NAME,
                Instant.now()
        );
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return new ModelCapabilities(
                PROVIDER_NAME,
                List.of("codellama:13b", "llama3:8b", "mistral:7b", "codellama:7b"),
                8192,
                4096,
                true,
                false,
                false,
                Map.of(
                        "codellama:13b", new ModelCapabilities.Pricing(0.0, 0.0),
                        "llama3:8b", new ModelCapabilities.Pricing(0.0, 0.0)
                )
        );
    }

    private record OllamaResponse(
            String id,
            String model,
            List<Choice> choices,
            Usage usage
    ) {
        record Choice(int index, Message message, String finishReason) {
            record Message(String role, String content) {}
        }
        record Usage(int promptTokens, int completionTokens, int totalTokens) {}
    }
}