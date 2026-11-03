package com.example.orchestrator.api;

import com.example.orchestrator.model.router.CostTracker;
import com.example.orchestrator.model.router.ModelRouter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/costs")
public class CostTrackingController {

    private final CostTracker costTracker;
    private final ModelRouter modelRouter;

    public CostTrackingController(CostTracker costTracker, ModelRouter modelRouter) {
        this.costTracker = costTracker;
        this.modelRouter = modelRouter;
    }

    @GetMapping("/total")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getTotalCosts() {
        return ResponseEntity.ok(Map.of(
            "totalPromptTokens", costTracker.getTotalPromptTokens(),
            "totalCompletionTokens", costTracker.getTotalCompletionTokens(),
            "totalCostUsd", costTracker.getTotalCostUsd(),
            "dailyCostUsd", costTracker.getDailyCostUsd(),
            "isOverDailyBudget", costTracker.isOverDailyBudget(),
            "isNearDailyBudget", costTracker.isNearDailyBudget()
        ));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getTaskCost(@PathVariable String taskId) {
        var taskCost = costTracker.getTaskCost(taskId);
        if (taskCost == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "taskId", taskCost.getTaskId(),
            "promptTokens", taskCost.getPromptTokens(),
            "completionTokens", taskCost.getCompletionTokens(),
            "costUsd", taskCost.getCostUsd(),
            "startTime", taskCost.getStartTime(),
            "lastUpdate", taskCost.getLastUpdate(),
            "isOverBudget", costTracker.isOverTaskBudget(taskId),
            "isNearBudget", costTracker.isNearTaskBudget(taskId)
        ));
    }

    @GetMapping("/daily")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getDailyCost() {
        var dailyCost = costTracker.getDailyCost();
        return ResponseEntity.ok(Map.of(
            "promptTokens", dailyCost.getPromptTokens(),
            "completionTokens", dailyCost.getCompletionTokens(),
            "timestamp", dailyCost.getTimestamp(),
            "costUsd", costTracker.getDailyCostUsd(),
            "isOverBudget", costTracker.isOverDailyBudget(),
            "isNearBudget", costTracker.isNearDailyBudget()
        ));
    }

    @PostMapping("/daily/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetDaily() {
        costTracker.resetDaily();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/providers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getProviderStatus() {
        return ResponseEntity.ok(Map.of(
            "availableProviders", modelRouter.getAvailableProviders(),
            "circuitBreakers", modelRouter.getCircuitBreakerStates(),
            "profiles", modelRouter.getProfileConfigs()
        ));
    }
}