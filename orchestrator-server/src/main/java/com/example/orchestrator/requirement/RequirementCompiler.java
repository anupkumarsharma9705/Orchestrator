package com.example.orchestrator.requirement;

import com.example.orchestrator.context.ContextBundle;
import com.example.orchestrator.model.router.ModelProfile;
import com.example.orchestrator.model.router.ModelRouter;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Compiles natural language context into structured requirement specifications.
 */
@Service
public class RequirementCompiler {

    private static final Logger log = LoggerFactory.getLogger(RequirementCompiler.class);

    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public RequirementCompiler(ModelRouter modelRouter, ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    public Mono<RequirementSpec> compile(ContextBundle contextBundle) {
        String prompt = buildPrompt(contextBundle);

        ChatRequest request = new ChatRequest(
                List.of(new ChatRequest.Message("user", prompt, null, null, null)),
                null,
                0.1,
                4096,
                0.9,
                null,
                null,
                false,
                "You are an expert software architect. Create detailed, actionable implementation plans.",
                null,
                null,
                null,
                null
        );

        return callWithRetry(() -> {
            return modelRouter.chatWithProfile(request, ModelProfile.PLANNER)
                    .map(chatResponse -> {
                        if (chatResponse == null || chatResponse.choices() == null || chatResponse.choices().isEmpty()) {
                            throw new IllegalStateException("Empty response from model router");
                        }
                        return chatResponse.choices().get(0).message().content();
                    })
                    .map(response -> {
                        String jsonResponse = extractJson(response);
                        try {
                            RequirementSpec spec = objectMapper.readValue(jsonResponse, RequirementSpec.class);
                            if (!spec.isValid()) {
                                throw new IllegalStateException("Generated requirement spec is invalid: missing functional requirements");
                            }
                            return spec;
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse requirement specification: " + e.getMessage(), e);
                        }
                    });
        });
    }

    private String buildPrompt(ContextBundle contextBundle) {
        return """
            You are a senior software architect. Convert the natural language request into structured engineering requirements.
            
            CONTEXT:
            %s
            
            TASK:
            Analyze the user request and the project context. Produce a detailed requirement specification in JSON format.
            
            Output ONLY valid JSON matching this exact schema:
            {
              "functionalRequirements": ["string"],
              "securityRequirements": ["string"],
              "performanceRequirements": ["string"],
              "reliabilityRequirements": ["string"],
              "testingRequirements": ["string"],
              "observabilityRequirements": ["string"],
              "configurationRequirements": ["string"],
              "deploymentRequirements": ["string"],
              "acceptanceCriteria": ["string"],
              "assumptions": ["string"],
              "risks": ["string"]
            }
            
            RULES:
            - Be specific and actionable. No vague statements.
            - If a category has no applicable requirements, use an empty array [].
            - Mark assumptions explicitly - do not invent requirements silently.
            - Identify risks that could impact implementation.
            - Acceptance criteria must be testable and measurable.
            - No markdown fences, no explanations, no extra text. ONLY the JSON object.
            """.formatted(contextBundle.toPromptContext());
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("`json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("`")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("`")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private <T> Mono<T> callWithRetry(java.util.function.Supplier<Mono<T>> supplier) {
        return supplier.get()
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(5))
                        .filter(throwable -> {
                            String msg = throwable.getMessage();
                            return msg != null && (msg.contains("429") 
                                    || msg.toLowerCase().contains("connection reset")
                                    || msg.contains("401"));
                        })
                        .doBeforeRetry(retrySignal -> {
                            log.warn("Retrying after failure: {}", retrySignal.failure().getMessage());
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                            new RuntimeException("Failed after retries: " + retrySignal.failure().getMessage(), retrySignal.failure()))
                );
    }
}