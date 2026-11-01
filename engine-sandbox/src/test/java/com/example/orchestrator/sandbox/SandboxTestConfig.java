package com.example.orchestrator.sandbox;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test configuration for Sandbox tests.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "com.example.orchestrator.sandbox",
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SandboxConfig.class)
)
public class SandboxTestConfig {
}