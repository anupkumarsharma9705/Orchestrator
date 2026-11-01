package com.example.orchestrator.core.hardening;

import com.example.orchestrator.core.state.StateManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for hardening components.
 */
@Configuration
public class HardeningAutoConfiguration {

    @Bean
    public PromptSanitizer promptSanitizer() {
        return new PromptSanitizer();
    }

    @Bean
    public SecretScanner secretScanner() {
        return new SecretScanner();
    }

    @Bean
    public CorrelationLogger correlationLogger() {
        return new CorrelationLogger();
    }

    @Bean
    public RetryPolicy retryPolicy() {
        return new RetryPolicy();
    }
}