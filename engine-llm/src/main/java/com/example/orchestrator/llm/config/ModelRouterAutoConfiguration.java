package com.example.orchestrator.llm.config;

import com.example.orchestrator.config.EgressFilterConfig;
import com.example.orchestrator.llm.CostTracker;
import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.llm.provider.NvidiaProvider;
import com.example.orchestrator.llm.provider.OpenRouterProvider;
import com.example.orchestrator.llm.provider.OllamaProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for the LLM layer.
 * Sets up providers, router, cost tracker, and profiles.
 */
@Configuration
@EnableConfigurationProperties(ModelRouterConfig.class)
public class ModelRouterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "orchestrator.model-router.providers.nvidia.enabled", havingValue = "true", matchIfMissing = true)
    public NvidiaProvider nvidiaProvider(CostTracker costTracker, EgressFilterConfig egressFilterConfig,
            @Value("${orchestrator.model-router.providers.nvidia.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${orchestrator.model-router.providers.nvidia.api-key:}") String apiKey) {
        return new NvidiaProvider(baseUrl, apiKey, costTracker, egressFilterConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "orchestrator.model-router.providers.openrouter.enabled", havingValue = "true", matchIfMissing = false)
    public OpenRouterProvider openRouterProvider(CostTracker costTracker, EgressFilterConfig egressFilterConfig,
            @Value("${orchestrator.model-router.providers.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${orchestrator.model-router.providers.openrouter.api-key:}") String apiKey) {
        return new OpenRouterProvider(baseUrl, apiKey, costTracker, egressFilterConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "orchestrator.model-router.providers.ollama.enabled", havingValue = "true", matchIfMissing = false)
    public OllamaProvider ollamaProvider(CostTracker costTracker, EgressFilterConfig egressFilterConfig,
            @Value("${orchestrator.model-router.providers.ollama.base-url:http://localhost:11434/v1}") String baseUrl) {
        return new OllamaProvider(baseUrl, costTracker, egressFilterConfig);
    }

    @Bean
    public CostTracker costTracker() {
        return new CostTracker();
    }

    @Bean
    public ModelRouter modelRouter(
            List<ModelProvider> providers,
            CostTracker costTracker,
            ModelRouterConfig config) {
        
        // Build profile map from config
        Map<String, com.example.orchestrator.model.ModelProfile> profiles = new java.util.HashMap<>();
        for (Map.Entry<String, ModelRouterConfig.ModelProfileConfig> entry : config.getProfiles().entrySet()) {
            var configProfile = entry.getValue();
            profiles.put(entry.getKey(), new com.example.orchestrator.model.ModelProfile(
                    configProfile.getProvider(),
                    configProfile.getModel(),
                    configProfile.getTemperature(),
                    configProfile.getMaxTokens(),
                    configProfile.getTopP(),
                    configProfile.getSystemPrompt(),
                    configProfile.isEnabled(),
                    configProfile.getFallbackProfiles()
            ));
        }

        // Create router with providers
        var providerMap = new java.util.HashMap<String, ModelProvider>();
        for (ModelProvider provider : providers) {
            providerMap.put(provider.getProviderName(), provider);
        }
        var router = new com.example.orchestrator.llm.ModelRouter(
                providerMap,
                costTracker,
                profiles,
                config
        );
        return router;
    }
}