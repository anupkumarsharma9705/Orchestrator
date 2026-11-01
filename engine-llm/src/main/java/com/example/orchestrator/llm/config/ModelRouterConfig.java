package com.example.orchestrator.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the model router.
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.model-router")
public class ModelRouterConfig {

    private String defaultProvider = "test";
    private String defaultProfile = "general";
    private int timeoutSeconds = 60;
    private int maxRetries = 3;
    private int retryDelayMs = 1000;

    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private CostTrackingConfig costTracking = new CostTrackingConfig();

    private Map<String, ProviderConfig> providers = new java.util.LinkedHashMap<>();
    private Map<String, ModelProfileConfig> profiles = new java.util.LinkedHashMap<>();

    // Getters and setters
    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    public String getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(String defaultProfile) { this.defaultProfile = defaultProfile; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public int getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(int retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public CostTrackingConfig getCostTracking() { return costTracking; }
    public void setCostTracking(CostTrackingConfig costTracking) { this.costTracking = costTracking; }

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    public Map<String, ModelProfileConfig> getProfiles() { return profiles; }
    public void setProfiles(Map<String, ModelProfileConfig> profiles) { this.profiles = profiles; }

    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private int failureThreshold = 5;
        private int timeoutSeconds = 30;
        private int halfOpenRequests = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getHalfOpenRequests() { return halfOpenRequests; }
        public void setHalfOpenRequests(int halfOpenRequests) { this.halfOpenRequests = halfOpenRequests; }
    }

    public static class CostTrackingConfig {
        private boolean enabled = true;
        private BigDecimal budgetPerTaskUsd = new BigDecimal("0.50");
        private BigDecimal budgetPerDayUsd = new BigDecimal("10.0");
        private double alertThresholdPercent = 80.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public BigDecimal getBudgetPerTaskUsd() { return budgetPerTaskUsd; }
        public void setBudgetPerTaskUsd(BigDecimal budgetPerTaskUsd) { this.budgetPerTaskUsd = budgetPerTaskUsd; }
        public BigDecimal getBudgetPerDayUsd() { return budgetPerDayUsd; }
        public void setBudgetPerDayUsd(BigDecimal budgetPerDayUsd) { this.budgetPerDayUsd = budgetPerDayUsd; }
        public double getAlertThresholdPercent() { return alertThresholdPercent; }
        public void setAlertThresholdPercent(double alertThresholdPercent) { this.alertThresholdPercent = alertThresholdPercent; }
    }

    public static class ProviderConfig {
        private boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private Map<String, String> models = new java.util.LinkedHashMap<>();
        private int rateLimitRpm = 60;
        private int timeoutSeconds = 60;
        private boolean supportsStreaming = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Map<String, String> getModels() { return models; }
        public void setModels(Map<String, String> models) { this.models = models; }
        public int getRateLimitRpm() { return rateLimitRpm; }
        public void setRateLimitRpm(int rateLimitRpm) { this.rateLimitRpm = rateLimitRpm; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isSupportsStreaming() { return supportsStreaming; }
        public void setSupportsStreaming(boolean supportsStreaming) { this.supportsStreaming = supportsStreaming; }
    }

    public static class ModelProfileConfig {
        private String provider;
        private String model;
        private double temperature = 0.2;
        private int maxTokens = 8192;
        private double topP = 0.95;
        private String systemPrompt;
        private boolean enabled = true;
        private List<String> fallbackProfiles = new java.util.ArrayList<>();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public double getTopP() { return topP; }
        public void setTopP(double topP) { this.topP = topP; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getFallbackProfiles() { return fallbackProfiles; }
        public void setFallbackProfiles(List<String> fallbackProfiles) { this.fallbackProfiles = fallbackProfiles; }
    }
}