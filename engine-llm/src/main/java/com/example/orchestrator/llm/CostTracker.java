package com.example.orchestrator.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks token usage and costs per task, project, and daily aggregates.
 * Enforces per-job and daily budget limits.
 * 
 * Fixes:
 * - M1: Memory leak - added TTL cleanup for old task/daily entries
 * - M2: Thread safety - removed synchronized + ConcurrentHashMap double sync; 
 *       using AtomicReference for configuration; pricing made configurable
 */
@Component("llmCostTracker")
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    // TTL for cleanup (default: 7 days for tasks, 30 days for daily)
    private static final long TASK_TTL_DAYS = 7;
    private static final long DAILY_TTL_DAYS = 30;

    // Pricing per 1K tokens in USD (prompt, completion) - now mutable for thread-safe updates
    private final AtomicReference<Map<String, Map<String, double[]>>> pricingRef = new AtomicReference<>(Map.of(
            "nvidia", Map.of(
                    "nvidia/nemotron-3-ultra", new double[]{0.0, 0.0}
            ),
            "openrouter", Map.of(
                    "nvidia/nemotron-3-ultra", new double[]{0.0, 0.0},
                    "openai/gpt-4o", new double[]{5.0, 15.0},
                    "anthropic/claude-3.5-sonnet", new double[]{3.0, 15.0},
                    "meta-llama/llama-3.1-405b", new double[]{0.5, 1.0}
            ),
            "ollama", Map.of(
                    "codellama:13b", new double[]{0.0, 0.0},
                    "llama3:8b", new double[]{0.0, 0.0},
                    "mistral:7b", new double[]{0.0, 0.0}
            ),
            "test", Map.of(
                    "test-model", new double[]{0.0, 0.0}
            )
    ));

    // Per-task costs (in-memory, mutable)
    private final Map<String, TaskCostInfo> taskCosts = new ConcurrentHashMap<>();

    // Daily aggregates
    private final Map<LocalDate, DailyCostInfo> dailyCosts = new ConcurrentHashMap<>();

    // Global aggregates
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalCostMicro = new AtomicLong(0);

    // Budget limits (configurable) - thread-safe via AtomicReference
    private final AtomicReference<BigDecimal> budgetPerTaskUsd = new AtomicReference<>(new BigDecimal("0.50"));
    private final AtomicReference<BigDecimal> budgetPerDayUsd = new AtomicReference<>(new BigDecimal("10.00"));
    private final AtomicReference<Double> alertThresholdPercent = new AtomicReference<>(80.0);

    /**
     * Records token usage and cost for a task.
     * M1: Periodically cleans up old entries to prevent memory leak.
     */
    public void recordUsage(String taskId, String model, String provider, long promptTokens, long completionTokens) {
        // M1: Cleanup old entries periodically (every 100 calls)
        maybeCleanup();

        // Calculate cost using current pricing
        Map<String, Map<String, double[]>> pricing = pricingRef.get();
        double[] pricingArr = pricing.getOrDefault(provider, Map.of()).getOrDefault(model, new double[]{0.0, 0.0});
        double promptCost = (promptTokens / 1000.0) * pricingArr[0];
        double completionCost = (completionTokens / 1000.0) * pricingArr[1];
        double totalCost = promptCost + completionCost;
        long costMicro = Math.round(totalCost * 1_000_000);

        // Update task cost (no synchronized needed - ConcurrentHashMap.compute is atomic)
        taskCosts.compute(taskId, (k, v) -> {
            if (v == null) {
                return new TaskCostInfo(taskId, model, provider, promptTokens, completionTokens, costMicro);
            }
            v.promptTokens += promptTokens;
            v.completionTokens += completionTokens;
            v.costMicro += costMicro;
            v.lastUpdate = Instant.now();
            return v;
        });

        // Update daily cost
        LocalDate today = LocalDate.now();
        dailyCosts.compute(today, (k, v) -> {
            if (v == null) {
                return new DailyCostInfo(promptTokens, completionTokens, costMicro);
            }
            v.promptTokens += promptTokens;
            v.completionTokens += completionTokens;
            v.costMicro += costMicro;
            v.updatedAt = Instant.now();
            return v;
        });

        // Update globals
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalCostMicro.addAndGet(costMicro);

        // Check budget alerts
        checkBudgetAlerts();
    }

    /**
     * M1: Periodic cleanup of expired entries.
     * Runs every ~100 calls to prevent memory leak.
     */
    private static final AtomicLong cleanupCounter = new AtomicLong(0);
    private static final int CLEANUP_INTERVAL = 100;

    private void maybeCleanup() {
        if (cleanupCounter.getAndIncrement() % CLEANUP_INTERVAL == 0) {
            cleanupExpiredEntries();
        }
    }

    /**
     * M1: Removes expired task and daily cost entries based on TTL.
     */
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        Instant taskCutoff = now.minus(TASK_TTL_DAYS, ChronoUnit.DAYS);
        Instant dailyCutoff = now.minus(DAILY_TTL_DAYS, ChronoUnit.DAYS);

        // Clean task costs
        taskCosts.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate.isBefore(taskCutoff)
        );

        // Clean daily costs
        dailyCosts.entrySet().removeIf(entry ->
            entry.getValue().updatedAt.isBefore(dailyCutoff)
        );

        log.debug("CostTracker cleanup: task entries {}, daily entries {}", 
            taskCosts.size(), dailyCosts.size());
    }

    /**
     * M2: Thread-safe pricing updates.
     * Allows runtime pricing configuration without stale reads.
     */
    public void updatePricing(String provider, String model, double promptCostPer1k, double completionCostPer1k) {
        pricingRef.updateAndGet(current -> {
            Map<String, Map<String, double[]>> updated = new java.util.HashMap<>(current);
            updated.computeIfAbsent(provider, k -> new java.util.HashMap<>())
                   .put(model, new double[]{promptCostPer1k, completionCostPer1k});
            return Map.copyOf(updated); // Immutable copy for thread safety
        });
        log.info("Updated pricing for {}/{}", provider, model);
    }

    public boolean isBudgetExceeded(String taskId) {
        TaskCostInfo info = taskCosts.get(taskId);
        if (info == null) return false;
        BigDecimal taskCostUsd = new BigDecimal(info.costMicro)
            .divide(BigDecimal.valueOf(1_000_000), RoundingMode.HALF_UP);
        return taskCostUsd.compareTo(budgetPerTaskUsd.get()) > 0;
    }

    public boolean isDailyBudgetExceeded() {
        DailyCostInfo today = dailyCosts.get(LocalDate.now());
        if (today == null) return false;
        BigDecimal dailyCostUsd = new BigDecimal(today.costMicro)
            .divide(BigDecimal.valueOf(1_000_000), RoundingMode.HALF_UP);
        return dailyCostUsd.compareTo(budgetPerDayUsd.get()) > 0;
    }

    public boolean isDailyBudgetAlert() {
        DailyCostInfo today = dailyCosts.get(LocalDate.now());
        if (today == null) return false;
        BigDecimal dailyCostUsd = new BigDecimal(today.costMicro)
            .divide(BigDecimal.valueOf(1_000_000), RoundingMode.HALF_UP);
        BigDecimal threshold = budgetPerDayUsd.get()
            .multiply(BigDecimal.valueOf(alertThresholdPercent.get() / 100.0));
        return dailyCostUsd.compareTo(threshold) >= 0;
    }

    public BigDecimal getTaskCostUsd(String taskId) {
        TaskCostInfo info = taskCosts.get(taskId);
        if (info == null) return BigDecimal.ZERO;
        return new BigDecimal(info.costMicro)
            .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal getDailyCostUsd(LocalDate date) {
        DailyCostInfo info = dailyCosts.get(date);
        if (info == null) return BigDecimal.ZERO;
        return new BigDecimal(info.costMicro)
            .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalCostUsd() {
        return new BigDecimal(totalCostMicro.get())
            .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }

    public long getTotalPromptTokens() { return totalPromptTokens.get(); }
    public long getTotalCompletionTokens() { return totalCompletionTokens.get(); }

    public Map<String, TaskCostInfo> getTaskCosts() { return Map.copyOf(taskCosts); }
    public Map<LocalDate, DailyCostInfo> getDailyCosts() { return Map.copyOf(dailyCosts); }

    // Configuration setters - thread-safe via AtomicReference
    public void setBudgetPerTaskUsd(BigDecimal budget) { this.budgetPerTaskUsd.set(budget); }
    public void setBudgetPerDayUsd(BigDecimal budget) { this.budgetPerDayUsd.set(budget); }
    public void setAlertThresholdPercent(double percent) { this.alertThresholdPercent.set(percent); }

    private void checkBudgetAlerts() {
        if (isDailyBudgetAlert()) {
            log.warn("Daily budget usage exceeded {}% threshold: ${}/{}",
                    alertThresholdPercent.get(), getDailyCostUsd(LocalDate.now()), budgetPerDayUsd.get());
        }
    }

    // Mutable record classes for mutable state
    private static class TaskCostInfo {
        final String taskId;
        final String model;
        final String provider;
        long promptTokens;
        long completionTokens;
        long costMicro;
        Instant startTime;
        Instant lastUpdate;

        TaskCostInfo(String taskId, String model, String provider, long promptTokens, long completionTokens, long costMicro) {
            this.taskId = taskId;
            this.model = model;
            this.provider = provider;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.costMicro = costMicro;
            this.startTime = Instant.now();
            this.lastUpdate = Instant.now();
        }
    }

    private static class DailyCostInfo {
        long promptTokens;
        long completionTokens;
        long costMicro;
        Instant updatedAt;

        DailyCostInfo(long promptTokens, long completionTokens, long costMicro) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.costMicro = costMicro;
            this.updatedAt = Instant.now();
        }
    }

    // Getters for monitoring
    public BigDecimal getBudgetPerTaskUsd() { return budgetPerTaskUsd.get(); }
    public BigDecimal getBudgetPerDayUsd() { return budgetPerDayUsd.get(); }
    public double getAlertThresholdPercent() { return alertThresholdPercent.get(); }
}