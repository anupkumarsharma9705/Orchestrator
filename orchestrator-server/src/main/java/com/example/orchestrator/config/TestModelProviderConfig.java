package com.example.orchestrator.config;

import com.example.orchestrator.model.router.ModelProvider;
import com.example.orchestrator.model.router.ModelCapabilities;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@Profile("test")
public class TestModelProviderConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean(ModelProvider.class)
    public ModelProvider testModelProvider() {
        System.out.println(">>> Creating testModelProvider bean");
        return new ModelProvider() {
            @Override
            public String getProviderName() {
                return "test";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public boolean supportsStreaming() {
                return false;
            }

            @Override
            public Mono<ChatResponse> chat(ChatRequest request) {
                String responseContent = "Test response for: " + request.messages().get(0).content();
                
                ChatResponse.Usage usage = new ChatResponse.Usage(100, 50, 150, null, null);
                ChatResponse.Message message = new ChatResponse.Message("assistant", responseContent, null);
                ChatResponse.Choice choice = new ChatResponse.Choice(0, message, "stop", null);
                
                return reactor.core.publisher.Mono.just(new ChatResponse(
                    "test-response-" + System.currentTimeMillis(),
                    "chat.completion",
                    System.currentTimeMillis() / 1000,
                    request.model(),
                    List.of(new ChatResponse.Choice(0, message, "stop", null)),
                    usage,
                    null,
                    "test",
                    null
                ));
            }

            @Override
            public Mono<String> chatStreaming(ChatRequest request) {
                return chat(request).map(this::serializeResponse);
            }

            @Override
            public ModelCapabilities getCapabilities() {
                return ModelCapabilities.defaultCapabilities();
            }

            private String serializeResponse(ChatResponse response) {
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
                } catch (Exception e) {
                    return "{}";
                }
            }
        };
    }
}