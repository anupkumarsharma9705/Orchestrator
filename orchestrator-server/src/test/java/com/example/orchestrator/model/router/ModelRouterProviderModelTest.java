package com.example.orchestrator.model.router;

import com.example.orchestrator.api.ProviderHealthController;
import com.example.orchestrator.core.hardening.PromptSanitizer;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ModelRouterProviderModelTest {

    private static final String CODER_PROFILE_MODEL = "nvidia/nemotron-3-ultra-550b-a55b";
    private static final String OPENROUTER_CODER_MODEL = "cohere/north-mini-code:free";

    @Test
    void coderFallbackUsesOpenRouterSpecificModel() {
        CapturingProvider openRouter = new CapturingProvider("openrouter");
        ModelRouter router = router(openRouter);

        ChatResponse response = router.chatWithProfile(coderRequest(null), ModelProfile.CODER)
            .block(Duration.ofSeconds(5));

        assertEquals("openrouter", response.provider());
        assertEquals(OPENROUTER_CODER_MODEL, openRouter.lastRequest().model());
    }

    @Test
    void explicitModelOverrideIsNotRewritten() {
        CapturingProvider openRouter = new CapturingProvider("openrouter");
        ModelRouter router = router(openRouter);

        router.chatWithProfile(coderRequest("custom/coder-model"), ModelProfile.CODER)
            .block(Duration.ofSeconds(5));

        assertEquals("custom/coder-model", openRouter.lastRequest().model());
    }

    private static ModelRouter router(CapturingProvider provider) {
        ModelRouterConfig config = new ModelRouterConfig(
            "openrouter",
            Map.of(
                "openrouter", new ModelRouterConfig.ProviderConfig(
                    true,
                    "https://openrouter.ai/api/v1",
                    "test-key",
                    "OPENROUTER_API_KEY",
                    Map.of("coder", OPENROUTER_CODER_MODEL),
                    60,
                    60,
                    true
                )
            ),
            Map.of(
                ModelProfile.CODER, new ModelProfileConfig(
                    ModelProfile.CODER,
                    "nvidia",
                    CODER_PROFILE_MODEL,
                    0.2,
                    8192,
                    0.95,
                    null,
                    null,
                    true,
                    List.of(),
                    Map.of()
                ),
                ModelProfile.GENERAL, ModelProfileConfig.defaultFor(ModelProfile.GENERAL)
            ),
            new ModelRouterConfig.CircuitBreakerConfig(true, 5, 30, 3),
            new ModelRouterConfig.CostTrackingConfig(true, 0.50, 10.0, 80.0),
            ModelProfile.GENERAL,
            60,
            3,
            1000
        );

        ModelRouter router = new ModelRouter(
            config,
            mock(CostTracker.class),
            List.of(provider),
            mock(ProviderHealthController.class),
            new PromptSanitizer()
        );
        router.init();
        return router;
    }

    private static ChatRequest coderRequest(String model) {
        return new ChatRequest(
            List.of(new ChatRequest.Message("user", "Write the code", null, null, null)),
            model,
            0.2,
            8192,
            0.95,
            null,
            null,
            false,
            "You are a coder",
            null,
            null,
            null,
            Map.of("taskId", "task-1")
        );
    }

    private static final class CapturingProvider implements ModelProvider {

        private final String name;
        private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

        private CapturingProvider(String name) {
            this.name = name;
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean supportsStreaming() {
            return true;
        }

        @Override
        public Mono<ChatResponse> chat(ChatRequest request) {
            lastRequest.set(request);
            return Mono.just(new ChatResponse(
                "response-1",
                "chat.completion",
                0L,
                request.model(),
                List.of(),
                null,
                null,
                name,
                1L
            ));
        }

        @Override
        public Mono<String> chatStreaming(ChatRequest request) {
            return Mono.just("");
        }

        @Override
        public ModelCapabilities getCapabilities() {
            return ModelCapabilities.defaultCapabilities();
        }

        private ChatRequest lastRequest() {
            return lastRequest.get();
        }
    }
}
