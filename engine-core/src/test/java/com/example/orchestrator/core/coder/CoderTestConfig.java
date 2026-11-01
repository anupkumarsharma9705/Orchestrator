package com.example.orchestrator.core.coder;

import com.example.orchestrator.core.hardening.CorrelationLogger;
import com.example.orchestrator.core.hardening.PromptSanitizer;
import com.example.orchestrator.core.hardening.RetryPolicy;
import com.example.orchestrator.core.hardening.SecretScanner;
import com.example.orchestrator.core.state.StateManager;
import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.llm.provider.TestModelProvider;
import com.example.orchestrator.store.ArtifactStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for Coder tests.
 */
@Configuration
@Profile("test")
public class CoderTestConfig {

    @Bean
    public ModelProvider testModelProvider() {
        return new TestModelProvider();
    }

    @Bean
    @Primary
    public ArtifactStore artifactStore() {
        return new ArtifactStore(null, null, null, null);
    }

    @Bean
    public CorrelationLogger correlationLogger() {
        return new CorrelationLogger();
    }

    @Bean
    public PromptSanitizer promptSanitizer() {
        return new PromptSanitizer();
    }

    @Bean
    public SecretScanner secretScanner() {
        return new SecretScanner();
    }

    @Bean
    public RetryPolicy retryPolicy() {
        return new RetryPolicy();
    }

    @Bean
    public StateManager stateManager(ArtifactStore artifactStore, CorrelationLogger correlationLogger) {
        return new StateManager(artifactStore, correlationLogger);
    }
}