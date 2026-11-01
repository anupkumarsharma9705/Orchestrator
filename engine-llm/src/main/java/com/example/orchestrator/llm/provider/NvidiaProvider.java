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

/**
 * NVIDIA Nemotron-3-Ultra provider via NVIDIA API.
 * H-14: Null-safe handling of API responses (usage may be null).
 */
@Component
public class NvidiaProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(NvidiaProvider.class);
    private static final String PROVIDER_NAME = "nvidia";

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;
    private final CostTracker costTracker;

    public NvidiaProvider(
            @Value("${orchestrator.model-router.providers.nvidia.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${orchestrator.model-router.providers.nvidia.api-key:}") String apiKey,
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
            return Mono.error(new IllegalStateException("NVIDIA provider not configured: missing API key"));
        }

        log.debug("Sending chat request to NVIDIA: model={}, messages={}", request.model(), request.messages().size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(request))
                .retrieve()
                .bodyToMono(NvidiaResponse.class)
                .map(this::toChatResponse)
                .doOnSuccess(response -> {
                    // H-14: Null-safe cost tracking
                    var usage = response.usage();
                    if (usage != null) {
                        long promptTokens = usage.promptTokens();
                        long completionTokens = usage.completionTokens();
                        costTracker.recordUsage(
                                request.metadata().getOrDefault("taskId", "unknown").toString(),
                                request.model(),
                                PROVIDER_NAME,
                                promptTokens,
                                completionTokens
                        );
                        log.debug("NVIDIA response received: id={}, tokens={}", response.id(), usage.totalTokens());
                    } else {
                        log.warn("NVIDIA response missing usage data: id={}", response.id());
                    }
                })
                .doOnError(error -> log.error("NVIDIA chat error: {}", error.getMessage()))
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
                        .toList(),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "top_p", request.topP(),
                "stream", false
        );
    }

    private ChatResponse toChatResponse(NvidiaResponse nvidiaResponse) {
        // H-14: Null-safe handling - API may return null usage or empty choices
        var choices = nvidiaResponse.choices();
        var firstChoice = (choices != null && !choices.isEmpty()) ? choices.get(0) : null;

        var usageObj = nvidiaResponse.usage();
        var usage = new ChatResponse.Usage(
                usageObj != null ? usageObj.promptTokens() : 0,
                usageObj != null ? usageObj.completionTokens() : 0,
                usageObj != null ? usageObj.totalTokens() : 0
        );

        // H-14: Null-safe fallback for message
        var message = (firstChoice != null && firstChoice.message() != null)
                ? firstChoice.message()
                : new NvidiaResponse.Message("assistant", "");

        var choice = new ChatResponse.Choice(
                0,
                new ChatResponse.Choice.Message(
                        message.role(),
                        message.content(),
                        List.of()
                ),
                firstChoice != null ? firstChoice.finishReason() : "unknown"
        );

        return new ChatResponse(
                nvidiaResponse.id() != null ? nvidiaResponse.id() : java.util.UUID.randomUUID().toString(),
                nvidiaResponse.model() != null ? nvidiaResponse.model() : "nvidia/nemotron-3-ultra",
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
                List.of("nvidia/nemotron-3-ultra"),
                128000,
                8192,
                true,
                true,
                false,
                Map.of("nvidia/nemotron-3-ultra", new ModelCapabilities.Pricing(0.0, 0.0))
        );
    }

    // NVIDIA API response DTOs
    private record NvidiaResponse(
            String id,
            String model,
            List<Choice> choices,
            Usage usage
    ) {
        record Choice(
                int index,
                Message message,
                String finishReason
        ) {}
        
        record Message(String role, String content) {}
        
        record Usage(int promptTokens, int completionTokens, int totalTokens) {}
    }
}