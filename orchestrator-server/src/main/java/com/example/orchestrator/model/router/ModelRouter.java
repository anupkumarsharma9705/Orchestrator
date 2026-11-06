package com.example.orchestrator.model.router;

import com.example.orchestrator.api.ProviderHealthController;
import com.example.orchestrator.core.hardening.PromptSanitizer;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

@Service("serverModelRouter")
public class ModelRouter {
    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelRouterConfig config;
    private final CostTracker costTracker;
    private final List<ModelProvider> providerList;
    private final ProviderHealthController healthController;
    private final PromptSanitizer promptSanitizer;
    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<ModelProfile, ModelProfileConfig> profileConfigs = new ConcurrentHashMap<>();

    public ModelRouter(ModelRouterConfig config, CostTracker costTracker, List<ModelProvider> providerList, ProviderHealthController healthController, PromptSanitizer promptSanitizer) {
        this.config = config;
        this.costTracker = costTracker;
        this.providerList = providerList;
        this.healthController = healthController;
        this.promptSanitizer = promptSanitizer;
    }

    @PostConstruct
    public void init() {
        // Initialize circuit breakers for each enabled provider from config
        config.providers().forEach((name, providerConfig) -> {
            if (Boolean.TRUE.equals(providerConfig.enabled())) {
                // H-10: Validate egress - block internal/metadata endpoints
                try {
                    EgressFilter.validateProviderBaseUrl(providerConfig.baseUrl(), name);
                } catch (IllegalArgumentException e) {
                    log.error("Egress validation failed for provider {}: {}", name, e.getMessage());
                    throw new IllegalStateException("Provider " + name + " failed egress validation: " + e.getMessage(), e);
                }

                CircuitBreaker cb = new CircuitBreaker(
                    name,
                    config.circuitBreaker().failureThreshold(),
                    config.circuitBreaker().timeoutSeconds(),
                    config.circuitBreaker().halfOpenRequests()
                );
                circuitBreakers.put(name, cb);
            }
        });

        // Initialize profile configs
        config.profiles().forEach((profile, profileConfig) -> {
            if (Boolean.TRUE.equals(profileConfig.enabled())) {
                profileConfigs.put(profile, profileConfig);
            }
        });

        // Register providers from injected list
        providerList.forEach(this::registerProvider);

        // Validate at least one provider is available. The server is still
        // useful without one (health checks, configuration, doctor), so this is
        // a warning rather than a startup failure. Generation requests fail
        // with an explicit "No available providers" error until a key is set.
        if (providers.isEmpty()) {
            log.warn("No LLM providers are available. Set a provider API key " +
                "(for example NVIDIA_API_KEY) before running generation requests.");
        }

        log.info("ModelRouter initialized with {} providers, {} profiles", providers.size(), profileConfigs.size());
    }

    private void registerProvider(ModelProvider provider) {
        if (provider != null && provider.isAvailable()) {
            providers.put(provider.getProviderName(), provider);
            log.info("Registered provider: {}", provider.getProviderName());
        }
    }

    public Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile) {
        ModelProfileConfig profileConfig = profileConfigs.getOrDefault(profile, profileConfigs.get(config.defaultProfile()));
        
        // Use profile-specific model if not specified in request
        String model = request.model() != null ? request.model() : 
            (profileConfig != null ? profileConfig.model() : null);
        
        // H-17: Sanitize prompt content to prevent injection attacks
        List<ChatRequest.Message> sanitizedMessages = request.messages().stream()
            .map(m -> new ChatRequest.Message(
                m.role(),
                promptSanitizer.sanitize(m.content()),
                m.name(),
                m.toolCalls(),
                m.toolCallId()
            ))
            .toList();
        
        String sanitizedSystemPrompt = request.systemPrompt() != null 
            ? promptSanitizer.sanitize(request.systemPrompt())
            : null;
        
        ChatRequest routedRequest = new ChatRequest(
            sanitizedMessages,
            model,
            request.temperature() != null ? request.temperature() : profileConfig.temperature(),
            request.maxTokens() != null ? request.maxTokens() : profileConfig.maxTokens(),
            request.topP() != null ? request.topP() : profileConfig.topP(),
            request.topK(),
            request.stopSequences(),
            request.stream(),
            sanitizedSystemPrompt != null ? sanitizedSystemPrompt : profileConfig.systemPrompt(),
            request.tools(),
            request.toolChoice(),
            request.responseFormat(),
            request.metadata()
        );
        
        return executeWithFailover(routedRequest, profile, profileConfig);
    }

    private Mono<ChatResponse> executeWithFailover(ChatRequest request, ModelProfile profile,
                                                   ModelProfileConfig profileConfig) {
        List<ModelProfile> fallbackChain = profileConfig != null && profileConfig.fallbackProfiles() != null
            ? profileConfig.fallbackProfiles()
            : List.of();

        // Try primary provider first
        String primaryProvider = profileConfig != null ? profileConfig.provider() : null;
        ModelProvider provider = primaryProvider != null ? providers.get(primaryProvider) : null;

        if (provider != null && canUseProvider(primaryProvider)) {
            return executeWithProvider(provider,
                withProviderModel(request, profile, profileConfig, primaryProvider), "primary");
        }

        // Try fallbacks
        for (ModelProfile fallbackProfile : fallbackChain) {
            ModelProfileConfig fallbackConfig = profileConfigs.get(fallbackProfile);
            if (fallbackConfig != null && fallbackConfig.provider() != null) {
                ModelProvider fallbackProvider = providers.get(fallbackConfig.provider());
                if (fallbackProvider != null && canUseProvider(fallbackConfig.provider())) {
                    log.warn("Falling back to provider: {} for profile: {}", fallbackConfig.provider(), fallbackProfile);
                    return executeWithProvider(fallbackProvider,
                        withProviderModel(request, profile, profileConfig, fallbackConfig.provider()),
                        "fallback-" + fallbackProfile);
                }
            }
        }

        // Try any available provider as last resort
        for (ModelProvider fallbackProvider : providers.values()) {
            String fallbackName = fallbackProvider.getProviderName();
            if (canUseProvider(fallbackName)) {
                log.warn("Using last-resort provider: {}", fallbackName);
                return executeWithProvider(fallbackProvider,
                    withProviderModel(request, profile, profileConfig, fallbackName), "last-resort");
            }
        }

        return Mono.error(new IllegalStateException("No available providers"));
    }

    /**
     * Rewrites the model for the provider that will actually execute the
     * request.
     *
     * <p>Profiles name the model for their primary provider (NVIDIA). A
     * fallback provider such as OpenRouter uses different model identifiers,
     * and sending the primary provider's slug produces a 4xx error. The
     * {@code providers.<name>.models} map holds the per-profile identifier for
     * each provider. An explicitly requested model is left untouched.</p>
     */
    private ChatRequest withProviderModel(ChatRequest request, ModelProfile profile,
                                          ModelProfileConfig profileConfig, String providerName) {
        if (request == null || profile == null || providerName == null || config.providers() == null) {
            return request;
        }

        ModelRouterConfig.ProviderConfig providerConfig = config.providers().get(providerName);
        if (providerConfig == null || providerConfig.models() == null) {
            return request;
        }

        String profileModel = profileConfig != null ? profileConfig.model() : null;
        if (request.model() != null && !request.model().equals(profileModel)) {
            return request;
        }

        String mappedModel = providerConfig.models().get(profileConfigKey(profile));
        if (mappedModel == null || mappedModel.isBlank() || mappedModel.equals(request.model())) {
            return request;
        }

        return new ChatRequest(
            request.messages(),
            mappedModel,
            request.temperature(),
            request.maxTokens(),
            request.topP(),
            request.topK(),
            request.stopSequences(),
            request.stream(),
            request.systemPrompt(),
            request.tools(),
            request.toolChoice(),
            request.responseFormat(),
            request.metadata()
        );
    }

    private String profileConfigKey(ModelProfile profile) {
        return switch (profile) {
            case PLANNER -> "planner";
            case CODER -> "coder";
            case REVIEWER -> "reviewer";
            case SECURITY_REASONING -> "security";
            case DOCUMENTATION -> "docs";
            case GENERAL -> "general";
        };
    }

    private boolean canUseProvider(String providerName) {
        CircuitBreaker cb = circuitBreakers.get(providerName);
        if (cb == null) return true; // No circuit breaker = allow
        
        if (!cb.allowRequest()) {
            log.warn("Circuit breaker OPEN for provider: {}", providerName);
            return false;
        }
        return true;
    }

    private Mono<ChatResponse> executeWithProvider(ModelProvider provider, ChatRequest request, String context) {
        Instant start = Instant.now();
        String taskId = request.metadata() != null ? (String) request.metadata().get("taskId") : "unknown";
        String providerName = provider.getProviderName();

        // M6: Apply timeout from config
        int timeoutSeconds = config.timeoutSeconds() != null ? config.timeoutSeconds() : 60;

        return provider.chat(request)
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> {
                
                // Null-safe cost tracking
                if (response.usage() != null) {
                    long promptTokens = response.usage().promptTokens() != null ? response.usage().promptTokens() : 0L;
                    long completionTokens = response.usage().completionTokens() != null ? response.usage().completionTokens() : 0L;
                    
                    if (promptTokens == 0 && completionTokens == 0) {
                        log.warn("Token usage not reported by provider {} for model {}", 
                            providerName, request.model());
                    }
                    
                    try {
                        double costUsd = calculateCost(providerName, promptTokens, completionTokens);
                        costTracker.recordUsage(
                            request.metadata() != null ? (String) request.metadata().get("taskId") : "unknown",
                            request.model(),
                            providerName,
                            promptTokens,
                            completionTokens,
                            costUsd
                        );
                    } catch (Exception e) {
                        log.warn("Cost tracking failed for provider {}: {}", providerName, e.getMessage());
                    }
                } else {
                    log.warn("Token usage not reported by provider {} for model {}", providerName, request.model());
                }
                
                CircuitBreaker cb = circuitBreakers.get(providerName);
                if (cb != null) cb.recordSuccess();
                
                long latencyMs = java.time.Duration.between(start, Instant.now()).toMillis();
                healthController.recordRequest(providerName, true, latencyMs);
                
                log.debug("Provider {} succeeded in {}ms (context: {})", providerName, latencyMs, context);
            })
            .doOnError(error -> {
                CircuitBreaker cb = circuitBreakers.get(providerName);
                if (cb != null) cb.recordFailure();
                
                long latencyMs = java.time.Duration.between(start, Instant.now()).toMillis();
                healthController.recordRequest(providerName, false, latencyMs);
                
                log.warn("Provider {} failed (context: {}): {}", providerName, context, error.getMessage());
            })
            .onErrorResume(error -> {
                // Don't retry here - let the caller handle failover
                return Mono.error(error);
            });
    }

    private double calculateCost(String providerName, long promptTokens, long completionTokens) {
        // Simplified cost calculation - in reality would use model-specific pricing
        String providerKey = providerName;
        if ("openrouter".equals(providerName)) {
            // OpenRouter pricing varies by model - simplified here
            return (promptTokens * 0.000001) + (completionTokens * 0.000002);
        }
        return 0.0;
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        return routeChat(request, config.defaultProfile());
    }

    public Mono<ChatResponse> chatWithProfile(ChatRequest request, ModelProfile profile) {
        return routeChat(request, profile);
    }

    public List<String> getAvailableProviders() {
        return providers.entrySet().stream()
            .filter(e -> canUseProvider(e.getKey()))
            .map(Map.Entry::getKey)
            .toList();
    }

    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        return circuitBreakers.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getState()
            ));
    }

    public Map<ModelProfile, ModelProfileConfig> getProfileConfigs() {
        return profileConfigs;
    }
}