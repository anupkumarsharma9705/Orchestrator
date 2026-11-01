package com.example.orchestrator.llm;

import com.example.orchestrator.llm.config.ModelRouterAutoConfiguration;
import com.example.orchestrator.llm.provider.TestModelProvider;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ChatRequest.Message;
import com.example.orchestrator.model.ModelProfile;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = {ModelRouterAutoConfiguration.class, TestConfig.class})
@ActiveProfiles("test")
class ModelRouterTest {

    @Autowired
    private ModelRouter modelRouter;

    @Autowired
    private CostTracker costTracker;

    @Test
    void routeChat_usesTestProvider() {
        ChatRequest request = new ChatRequest(
                List.of(new Message("user", "Generate a simple REST controller", null, List.of())),
                "test-model",
                0.2, 1000, 0.95,
                "You are a code generator",
                List.of(),
                Map.of("taskId", "test-1", "profile", "coder")
        );

        ChatResponse response = modelRouter.chat(request).block();

        assertThat(response).isNotNull();
        assertThat(response.provider()).isEqualTo("test");
        assertThat(response.choices()).isNotEmpty();
    }

    @Test
    void routeChat_withProfile_usesProfileConfig() {
        ChatRequest request = new ChatRequest(
                List.of(new Message("user", "Plan a REST API for books", null, List.of())),
                "test-model",
                0.1, 1000, 0.9,
                "You are a planner",
                List.of(),
                Map.of("taskId", "test-plan", "profile", "planner")
        );

        ChatResponse response = modelRouter.chatWithProfile(request, "planner").block();

        assertThat(response).isNotNull();
        assertThat(response.provider()).isEqualTo("test");
    }

    @Test
    void costTracker_recordsUsage() {
        String taskId = "cost-test-" + System.currentTimeMillis();
        
        ChatRequest request = new ChatRequest(
                List.of(new Message("user", "Test cost tracking", null, List.of())),
                "test-model",
                0.2, 100, 50,
                "Test",
                List.of(),
                Map.of("taskId", taskId, "profile", "general")
        );

        modelRouter.chat(request).block();

        // Test model is free, so cost should be 0
        assertThat(costTracker.getTaskCostUsd(taskId)).isEqualByComparingTo("0.00");
    }

    @Test
    void getAvailableProviders_includesTest() {
        List<String> providers = modelRouter.getAvailableProviders();
        assertThat(providers).contains("test");
    }

    @Test
    void circuitBreakerStates_accessible() {
        Map<String, io.github.resilience4j.circuitbreaker.CircuitBreaker.State> states = modelRouter.getCircuitBreakerStates();
        assertThat(states).containsKey("test");
    }
}