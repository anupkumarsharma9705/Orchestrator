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
public class OpenRouterProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterProvider.class);
    private static final String PROVIDER_NAME = "openrouter";

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final CostTracker costTracker;

    public OpenRouterProvider(
            @Value("${orchestrator.model-router.providers.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${orchestrator.model-router.providers.openrouter.api-key:}") String apiKey,
            CostTracker costTracker,
            EgressFilterConfig filteredWebClientBuilder) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.costTracker = costTracker;
        this.webClient = filteredWebClientBuilder.filteredWebClientBuilder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", "https://github.com/java-companion")
                .defaultHeader("X-Title", "Java Companion Orchestrator")
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("OpenRouter provider not configured: missing API key"));
        }

        log.debug("Sending chat request to OpenRouter: model={}, messages={}", request.model(), request.messages().size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(request))
                .retrieve()
                .bodyToMono(OpenRouterResponse.class)
                .map(this::toChatResponse)
                .doOnSuccess(response -> {
                    costTracker.recordUsage(
                            request.metadata().getOrDefault("taskId", "unknown").toString(),
                            request.model(),
                            PROVIDER_NAME,
                            response.usage().promptTokens(),
                            response.usage().completionTokens()
                    );
                    log.debug("OpenRouter response received: id={}, tokens={}", response.id(), response.usage().totalTokens());
                })
                .doOnError(error -> log.error("OpenRouter chat error: {}", error.getMessage()))
                .timeout(Duration.ofSeconds(60));
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

    private ChatResponse toChatResponse(OpenRouterResponse openRouterResponse) {
        var firstChoice = openRouterResponse.choices().get(0);
        var usage = new ChatResponse.Usage(
                openRouterResponse.usage().promptTokens(),
                openRouterResponse.usage().completionTokens(),
                openRouterResponse.usage().totalTokens()
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
                openRouterResponse.id(),
                openRouterResponse.model(),
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
                List.of(
                        "nvidia/nemotron-3-ultra",
                        "openai/gpt-4o",
                        "anthropic/claude-3.5-sonnet",
                        "meta-llama/llama-3.1-405b"
                ),
                128000,
                8192,
                true,
                true,
                false,
                Map.of(
                        "nvidia/nemotron-3-ultra", new ModelCapabilities.Pricing(0.0, 0.0),
                        "openai/gpt-4o", new ModelCapabilities.Pricing(5.0, 15.0),
                        "anthropic/claude-3.5-sonnet", new ModelCapabilities.Pricing(3.0, 15.0)
                )
        );
    }

    private record OpenRouterResponse(
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