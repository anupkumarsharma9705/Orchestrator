package com.example.orchestrator.core.clarifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Clarifier.
 */
@ConfigurationProperties(prefix = "orchestrator.clarifier")
public class ClarifierConfig {

    private String systemPrompt = """
        You are an expert software architect who helps clarify vague software requirements.
        Ask specific, targeted questions that resolve architectural ambiguity.
        Return questions as a JSON array only - no markdown, no extra text.
        Each question must have: id, question, default, type (SELECT/INPUT/BOOLEAN), options.
        """;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
