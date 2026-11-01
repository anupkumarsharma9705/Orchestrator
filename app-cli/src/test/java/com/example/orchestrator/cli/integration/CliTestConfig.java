package com.example.orchestrator.cli.integration;

import com.example.orchestrator.templates.TemplateEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing both CommandLine and TemplateEngine for CLI integration tests.
 */
@Configuration
public class CliTestConfig {

    @Bean
    @Primary
    public TemplateEngine templateEngine() {
        return new TemplateEngine();
    }
}