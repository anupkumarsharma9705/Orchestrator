package com.example.orchestrator.task;

import com.example.orchestrator.context.ContextBundle;
import com.example.orchestrator.model.router.ModelProfile;
import com.example.orchestrator.model.router.ModelRouter;
import com.example.orchestrator.model.router.dto.ChatRequest;
import com.example.orchestrator.model.router.dto.ChatResponse;
import com.example.orchestrator.requirement.RequirementSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plans tasks based on requirements and context.
 */
@Service
public class TaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(TaskPlanner.class);

    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public TaskPlanner(ModelRouter modelRouter, ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    public Mono<TaskPlan> plan(RequirementSpec requirements, ContextBundle contextBundle) {
        String prompt = buildPrompt(requirements, contextBundle);

        ChatRequest request = new ChatRequest(
                List.of(new ChatRequest.Message("user", prompt, null, null, null)),
                null,
                0.1,
                4096,
                0.9,
                null,
                null,
                false,
                "You are a senior software architect and technical lead. Convert the structured requirements into an ordered implementation plan.",
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
                            TaskPlan plan = objectMapper.readValue(jsonResponse, TaskPlan.class);
                            if (plan.tasks() == null || plan.tasks().isEmpty()) {
                                throw new IllegalStateException("Generated task plan is empty");
                            }
                            return plan;
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse task plan: " + e.getMessage(), e);
                        }
                    });
        });
    }

    private String buildPrompt(RequirementSpec requirements, ContextBundle contextBundle) {
        return """
            You are a senior software architect and technical lead. Convert the structured requirements into an ordered implementation plan.
            
            CONTEXT:
            %s
            
            REQUIREMENTS:
            Functional: %s
            Security: %s
            Performance: %s
            Reliability: %s
            Testing: %s
            Observability: %s
            Configuration: %s
            Deployment: %s
            Acceptance Criteria: %s
            Assumptions: %s
            Risks: %s
            
            TASK:
            Create an ordered implementation plan. Each task must be specific, actionable, and independently verifiable.
            
            Output ONLY valid JSON matching this exact schema:
            {
              "tasks": [
                {
                  "id": "TASK-001",
                  "description": "string",
                  "affectedFiles": ["string"],
                  "dependencies": ["TASK-XXX"],
                  "risk": "LOW|MEDIUM|HIGH|CRITICAL",
                  "tools": ["listDirectory", "readFile", "searchFiles", "gitStatus", "gitDiff", "runMavenTest"],
                  "acceptanceCriteria": ["string"],
                  "verificationSteps": ["string"]
                }
              ],
              "summary": "string",
              "estimatedRounds": 3
            }
            
            RULES:
            - Order tasks by dependency (dependencies first).
            - Risk must be one of: LOW, MEDIUM, HIGH, CRITICAL.
            - Tools must be from the SAFE tool list only.
            - Each task must have at least one acceptance criterion.
            - Estimated rounds: 1-5.
            - No markdown fences, no explanations, no extra text. ONLY the JSON object.
            """.formatted(
                contextBundle.toPromptContext(),
                formatList(requirements.functionalRequirements()),
                formatList(requirements.securityRequirements()),
                formatList(requirements.performanceRequirements()),
                formatList(requirements.reliabilityRequirements()),
                formatList(requirements.testingRequirements()),
                formatList(requirements.observabilityRequirements()),
                formatList(requirements.configurationRequirements()),
                formatList(requirements.deploymentRequirements()),
                formatList(requirements.acceptanceCriteria()),
                formatList(requirements.assumptions()),
                formatList(requirements.risks())
            );
    }

    private String formatList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return list.stream().map(s -> "\"" + s.replace("\"", "\\\"") + "\"").collect(Collectors.joining(", ", "[", "]"));
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