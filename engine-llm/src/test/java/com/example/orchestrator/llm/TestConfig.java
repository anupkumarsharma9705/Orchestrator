package com.example.orchestrator.llm;

import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.llm.provider.TestModelProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration to register test-specific beans.
 */
@Configuration
@Profile("test")
public class TestConfig {

    @Bean
    public ModelProvider testModelProvider() {
        return new TestModelProvider();
    }
}