package com.example.orchestrator.core.planner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Planner.
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.planner")
public class PlannerConfig {

    private String systemPrompt = """
        You are an expert software architect and project planner.
        Create detailed, actionable implementation plans for Spring Boot REST APIs.
        Return tasks as a JSON array only - no markdown, no extra text.
        Each task must have: id, title, description, files[], dependsOn[], requirementIds[], acceptance[].
        """;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}