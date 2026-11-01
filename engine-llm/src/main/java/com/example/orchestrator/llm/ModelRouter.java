package com.example.orchestrator.llm;

import com.example.orchestrator.llm.config.ModelRouterConfig;
import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ModelCapabilities;
import com.example.orchestrator.model.ModelProfile;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Routes LLM requests to providers with failover, circuit breaker, and cost tracking.
 * Supports multiple profiles (PLANNER, CODER, REVIEWER, etc.) with configurable fallbacks.
 */
@Service("llmModelRouter")
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CostTracker costTracker;
    private final Map<String, ModelProfile> profiles;
    private final ModelRouterConfig config;

    // Default timeout for LLM requests (30 seconds)
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    public ModelRouter(Map<String, ModelProvider> providers, CostTracker costTracker,
            Map<String, ModelProfile> profiles, ModelRouterConfig config) {
        this.costTracker = costTracker;
        this.profiles = profiles;
        this.config = config;

        // Providers are auto-wired via the map
        this.providers.putAll(providers);
        
        for (Map.Entry<String, ModelProvider> entry : providers.entrySet()) {
            if (entry.getValue().isAvailable()) {
                log.info("Registered LLM provider: {}", entry.getKey());
            } else {
                log.warn("Provider {} not available (missing config)", entry.getKey());
            }
        }

        // Initialize circuit breakers with shared registry for proper cleanup (H7)
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        for (String providerName : providers.keySet()) {
            circuitBreakers.put(providerName, circuitBreakerRegistry.circuitBreaker(providerName));
        }
    }

    @PostConstruct
    public void init() {
        if (providers.isEmpty()) {
            log.warn("No LLM providers registered!");
        }
    }

    public Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile) {
        List<String> providerChain = buildProviderChain(profile);
        return tryProviderChain(request, providerChain, 0);
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        ModelProfile defaultProfile = profiles.get(config.getDefaultProfile());
        return routeChat(request, defaultProfile);
    }

    public Mono<ChatResponse> chatWithProfile(ChatRequest request, String profileName) {
        ModelProfile profile = profiles.get(profileName);
        if (profile == null) {
            return Mono.error(new IllegalArgumentException("Unknown profile: " + profileName));
        }
        return routeChat(request, profile);
    }

    private Mono<ChatResponse> tryProviderChain(ChatRequest request, List<String> providerChain, int index) {
        if (index >= providerChain.size()) {
            return Mono.error(new IllegalStateException(
                    "All providers exhausted for request: " + providerChain));
        }

        String providerName = providerChain.get(index);
        ModelProvider provider = providers.get(providerName);

        if (provider == null || !provider.isAvailable()) {
            log.warn("Provider {} not available, trying next", providerName);
            return tryProviderChain(request, providerChain, index + 1);
        }

        CircuitBreaker cb = circuitBreakers.get(providerName);
        if (cb.getState() == CircuitBreaker.State.OPEN) {
            log.warn("Circuit breaker OPEN for {}, trying next provider", providerName);
            return tryProviderChain(request, providerChain, index + 1);
        }

        String taskId = request.metadata().getOrDefault("taskId", "unknown").toString();
        ChatRequest enrichedRequest = new ChatRequest(
                request.messages(),
                request.model(),
                request.temperature(),
                request.maxTokens(),
                request.topP(),
                request.systemPrompt(),
                request.tools(),
                Map.ofEntries(
                        Map.entry("taskId", taskId),
                        Map.entry("profile", request.metadata().getOrDefault("profile", "general"))
                )
        );

        return provider.chat(enrichedRequest)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakers.get(providerName)))
                // H8: Add timeout to prevent hanging requests
                .timeout(DEFAULT_REQUEST_TIMEOUT)
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Provider {} timed out after {}s for task {}", providerName, DEFAULT_REQUEST_TIMEOUT.getSeconds(), taskId);
                    return tryProviderChain(request, providerChain, index + 1);
                })
                .doOnSuccess(response -> log.debug("Provider {} succeeded for task {}", providerName, taskId))
                .onErrorResume(e -> {
                    log.warn("Provider {} failed for task {}: {}, trying next", providerName, taskId, e.getMessage());
                    if (isRetryableError(e)) {
                        return tryProviderChain(request, providerChain, index + 1);
                    }
                    return Mono.error(e);
                });
    }

    private boolean isRetryableError(Throwable e) {
        return e instanceof java.util.concurrent.TimeoutException ||
               e.getMessage() != null && (e.getMessage().contains("429") || 
                                          e.getMessage().contains("timeout") ||
                                          e.getMessage().contains("connection"));
    }

    private List<String> buildProviderChain(ModelProfile profile) {
        var chain = new java.util.ArrayList<String>();
        chain.add(profile.provider());
        chain.addAll(profile.fallbackProfiles());
        for (String providerName : providers.keySet()) {
            if (!chain.contains(providerName)) {
                chain.add(providerName);
            }
        }
        return chain;
    }

    public List<String> getAvailableProviders() {
        return providers.values().stream()
                .filter(ModelProvider::isAvailable)
                .map(ModelProvider::getProviderName)
                .collect(Collectors.toList());
    }

    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        return circuitBreakers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getState()));
    }

    public CostTracker getCostTracker() {
        return costTracker;
    }

    public Map<String, ModelProvider> getProviders() {
        return Map.copyOf(providers);
    }

    /**
     * Cleanup circuit breakers on shutdown (H7).
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up circuit breakers for {} providers", circuitBreakers.size());
        circuitBreakers.clear();
        // Close the registry to release resources
        try {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
                try {
                    cb.transitionToDisabledState();
                } catch (Exception ex) {
                    log.debug("Error during circuit breaker state transition: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("Error during circuit breaker cleanup: {}", e.getMessage());
        }
    }
}