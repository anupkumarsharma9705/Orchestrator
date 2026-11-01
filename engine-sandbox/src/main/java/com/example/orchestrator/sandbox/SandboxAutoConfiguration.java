package com.example.orchestrator.sandbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Sandbox module.
 */
@Configuration
@EnableConfigurationProperties(SandboxConfig.class)
public class SandboxAutoConfiguration {

    @Bean
    public SandboxService sandboxService(SandboxConfig config) {
        return new SandboxService(config);
    }
}