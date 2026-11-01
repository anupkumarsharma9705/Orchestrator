package com.example.orchestrator.core.fixer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration for the Fixer component.
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.fixer")
@Validated
public class FixerConfig {

    @Positive
    @Max(50)
    private int maxFindingsPerPrompt = 10;

    @Min(0)
    @Max(1)
    private double temperature = 0.1;

    private boolean includeSurroundingContext = true;
    private int contextLines = 10;

    public int getMaxFindingsPerPrompt() { return maxFindingsPerPrompt; }
    public void setMaxFindingsPerPrompt(int maxFindingsPerPrompt) { this.maxFindingsPerPrompt = maxFindingsPerPrompt; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public boolean isIncludeSurroundingContext() { return includeSurroundingContext; }
    public void setIncludeSurroundingContext(boolean includeSurroundingContext) { this.includeSurroundingContext = includeSurroundingContext; }

    public int getContextLines() { return contextLines; }
    public void setContextLines(int contextLines) { this.contextLines = contextLines; }
}