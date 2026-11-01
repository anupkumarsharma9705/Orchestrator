package com.example.orchestrator.llm.provider;

import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ModelCapabilities;
import reactor.core.publisher.Mono;

/**
 * Interface for LLM providers.
 * Implementations must handle provider-specific API calls.
 */
public interface ModelProvider {

    /**
     * Returns the unique provider name (e.g., "nvidia", "openrouter", "ollama").
     */
    String getProviderName();

    /**
     * Checks if the provider is available and configured.
     */
    boolean isAvailable();

    /**
     * Whether the provider supports streaming responses.
     */
    boolean supportsStreaming();

    /**
     * Sends a chat request and returns the response.
     * 
     * @param request The chat request with messages, model, parameters
     * @return Mono emitting the chat response
     */
    Mono<ChatResponse> chat(ChatRequest request);

    /**
     * Sends a streaming chat request.
     * 
     * @param request The chat request with streaming enabled
     * @return Mono emitting the streamed response content
     */
    default Mono<String> chatStreaming(ChatRequest request) {
        return chat(request).map(ChatResponse::content);
    }

    /**
     * Returns the capabilities of this provider (supported models, features).
     */
    ModelCapabilities getCapabilities();
}