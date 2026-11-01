package com.example.orchestrator.core.coder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Coder.
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.coder")
public class CoderConfig {

    private String systemPrompt = """
        You are an expert software engineer. Write clean, correct, production-ready code.
        Follow all conventions and security rules strictly.
        Output ONLY tool calls in the specified JSON format.
        """;

    private int maxFileSizeKb = 100;
    private int maxFilesPerTask = 20;
    private int maxRetries = 3;
    private int compileTimeoutSeconds = 120;

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public int getMaxFileSizeKb() { return maxFileSizeKb; }
    public void setMaxFileSizeKb(int maxFileSizeKb) { this.maxFileSizeKb = maxFileSizeKb; }
    public int getMaxFilesPerTask() { return maxFilesPerTask; }
    public void setMaxFilesPerTask(int maxFilesPerTask) { this.maxFilesPerTask = maxFilesPerTask; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getCompileTimeoutSeconds() { return compileTimeoutSeconds; }
    public void setCompileTimeoutSeconds(int compileTimeoutSeconds) { this.compileTimeoutSeconds = compileTimeoutSeconds; }
}