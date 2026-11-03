package com.example.orchestrator.api;

import com.example.orchestrator.model.router.CircuitBreaker;
import com.example.orchestrator.model.router.CostTracker;
import com.example.orchestrator.model.router.ModelProfile;
import com.example.orchestrator.model.router.ModelRouter;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class ProviderHealthController {

    private final ModelRouter modelRouter;
    private final CostTracker costTracker;
    
    // Track metrics per provider
    private final Map<String, Long> totalRequests = new HashMap<>();
    private final Map<String, Long> successfulRequests = new HashMap<>();
    private final Map<String, Long> failedRequests = new HashMap<>();
    private final Map<String, Long> totalLatencyMs = new HashMap<>();
    private final Map<String, Long> lastSuccessTimestamp = new HashMap<>();
    private final Map<String, Long> lastFailureTimestamp = new HashMap<>();

    public ProviderHealthController(@Lazy ModelRouter modelRouter, CostTracker costTracker) {
        this.modelRouter = modelRouter;
        this.costTracker = costTracker;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getProviderHealth() {
        var providers = modelRouter.getAvailableProviders();
        var circuitBreakers = modelRouter.getCircuitBreakerStates();
        
        Map<String, Object> response = new LinkedHashMap<>();
        boolean anyDown = false;
        boolean anyDegraded = false;
        
        Map<String, Object> providerDetails = new LinkedHashMap<>();
        
        for (String providerName : providers) {
            String cbState = circuitBreakers.getOrDefault(providerName, CircuitBreaker.State.CLOSED).name();
            
            Map<String, Object> details = getProviderHealth(providerName, cbState);
            providerDetails.put(providerName, details);
            
            String status = (String) details.get("status");
            if ("DOWN".equals(status)) {
                anyDown = true;
            } else if ("DEGRADED".equals(status)) {
                anyDegraded = true;
            }
        }
        
        // Add cost tracking info
        Map<String, Object> costTracking = Map.of(
            "enabled", costTracker != null && costTracker.getTotalCostUsd() >= 0,
            "totalCostUsd", costTracker != null ? costTracker.getTotalCostUsd() : 0.0,
            "dailyCostUsd", costTracker != null ? costTracker.getDailyCostUsd() : 0.0,
            "dailyBudgetUsd", 10.0,
            "dailyBudgetUsedPercent", costTracker != null && costTracker.getDailyCostUsd() > 0 
                ? (costTracker.getDailyCostUsd() / 10.0 * 100) : 0.0
        );
        
        response.put("providers", providerDetails);
        response.put("costTracking", costTracking);
        response.put("overallStatus", anyDown ? "DOWN" : (anyDegraded ? "DEGRADED" : "UP"));
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    public void recordRequest(String providerName, boolean success, long latencyMs) {
        totalRequests.merge(providerName, 1L, Long::sum);
        if (success) {
            successfulRequests.merge(providerName, 1L, Long::sum);
            lastSuccessTimestamp.put(providerName, System.currentTimeMillis());
        } else {
            failedRequests.merge(providerName, 1L, Long::sum);
            lastFailureTimestamp.put(providerName, System.currentTimeMillis());
        }
        totalLatencyMs.merge(providerName, latencyMs, Long::sum);
    }
    
    private Map<String, Object> getProviderHealth(String providerName, String circuitBreakerState) {
        long total = totalRequests.getOrDefault(providerName, 0L);
        long success = successfulRequests.getOrDefault(providerName, 0L);
        long failed = failedRequests.getOrDefault(providerName, 0L);
        long totalLat = totalLatencyMs.getOrDefault(providerName, 0L);
        
        double avgLatency = total > 0 ? (double) totalLat / total : 0.0;
        double successRate = total > 0 ? (double) success / total : 1.0;
        
        String status;
        if (total == 0) {
            status = "UNKNOWN";
        } else if ("OPEN".equals(circuitBreakerState)) {
            status = "DOWN";
        } else if (successRate < 0.5 || "HALF_OPEN".equals(circuitBreakerState)) {
            status = "DEGRADED";
        } else {
            status = "UP";
        }
        
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status);
        details.put("circuitBreaker", circuitBreakerState);
        details.put("totalRequests", total);
        details.put("successfulRequests", success);
        details.put("failedRequests", failed);
        details.put("successRate", total > 0 ? (double) success / total : 1.0);
        details.put("avgLatencyMs", avgLatency);
        details.put("lastSuccess", lastSuccessTimestamp.get(providerName) != null 
            ? Instant.ofEpochMilli(lastSuccessTimestamp.get(providerName)).toString() : null);
        details.put("lastFailure", lastFailureTimestamp.get(providerName) != null 
            ? Instant.ofEpochMilli(lastFailureTimestamp.get(providerName)).toString() : null);
        details.put("successRateValue", successRate);
        
        return details;
    }
}