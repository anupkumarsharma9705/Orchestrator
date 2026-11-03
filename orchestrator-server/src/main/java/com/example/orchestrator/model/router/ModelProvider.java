package com.example.orchestrator.model.router;

import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface ModelProvider {
    
    String getProviderName();
    
    boolean isAvailable();
    
    boolean supportsStreaming();
    
    Mono<ChatResponse> chat(ChatRequest request);
    
    Mono<String> chatStreaming(ChatRequest request);
    
    ModelCapabilities getCapabilities();
    
    default boolean supportsFeature(ModelFeature feature) {
        return getCapabilities().supports(feature);
    }
}