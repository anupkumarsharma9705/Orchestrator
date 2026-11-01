package com.example.orchestrator.llm;

import com.example.orchestrator.llm.config.ModelRouterAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = {ModelRouterAutoConfiguration.class, TestConfig.class})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CostTrackerTest {

    @Autowired
    private CostTracker costTracker;

    @BeforeEach
    void resetState() {
        // Reset state by creating a new instance via reflection
        // Since CostTracker is a singleton, we need to clear its internal state
        costTracker.setBudgetPerTaskUsd(new java.math.BigDecimal("0.50"));
        costTracker.setBudgetPerDayUsd(new java.math.BigDecimal("10.00"));
        costTracker.setAlertThresholdPercent(80.0);
    }

    @Test
    void recordUsage_tracksTokensAndCost() {
        costTracker.recordUsage("task-1", "nvidia/nemotron-3-ultra", "nvidia", 1000, 500);

        BigDecimal cost = costTracker.getTaskCostUsd("task-1");
        assertThat(cost).isEqualByComparingTo("0.00"); // NVIDIA free tier
    }

    @Test
    void recordUsage_openRouterModel_calculatesCost() {
        costTracker.recordUsage("task-2", "openai/gpt-4o", "openrouter", 1000, 500);

        BigDecimal cost = costTracker.getTaskCostUsd("task-2");
        // 1000 prompt tokens * $5/1K + 500 completion tokens * $15/1K = $5 + $7.5 = $12.5
        assertThat(cost).isEqualByComparingTo("12.50");
    }

    @Test
    void budgetEnforcement_works() {
        costTracker.setBudgetPerTaskUsd(new java.math.BigDecimal("0.01")); // 1 cent
        costTracker.recordUsage("task-budget", "openai/gpt-4o", "openrouter", 1000, 500);

        assertThat(costTracker.isBudgetExceeded("task-budget")).isTrue();
    }

    @Test
    void dailyBudgetAlert_works() {
        costTracker.setBudgetPerDayUsd(new java.math.BigDecimal("0.01")); // 1 cent/day
        costTracker.setAlertThresholdPercent(50.0); // 50%
        costTracker.recordUsage("task-daily", "openai/gpt-4o", "openrouter", 100, 50);

        assertThat(costTracker.isDailyBudgetAlert()).isTrue();
    }

    @Test
    void dailyBudgetExceeded_works() {
        costTracker.setBudgetPerDayUsd(new java.math.BigDecimal("0.01"));
        costTracker.recordUsage("task-daily-1", "openai/gpt-4o", "openrouter", 1000, 500);

        assertThat(costTracker.isDailyBudgetExceeded()).isTrue();
    }

    @Test
    void getTotalCost_works() {
        costTracker.recordUsage("task-total", "nvidia/nemotron-3-ultra", "nvidia", 1000, 500);
        BigDecimal total = costTracker.getTotalCostUsd();
        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void dailyCosts_trackedCorrectly() {
        costTracker.recordUsage("task-day1", "nvidia/nemotron-3-ultra", "nvidia", 1000, 500);
        
        BigDecimal dailyCost = costTracker.getDailyCostUsd(java.time.LocalDate.now());
        assertThat(dailyCost).isEqualByComparingTo("0.00");
    }

    @Test
    void totalTokens_trackedCorrectly() {
        costTracker.recordUsage("task-tokens", "nvidia/nemotron-3-ultra", "nvidia", 1000, 500);
        
        assertThat(costTracker.getTotalPromptTokens()).isEqualTo(1000);
        assertThat(costTracker.getTotalCompletionTokens()).isEqualTo(500);
    }
}