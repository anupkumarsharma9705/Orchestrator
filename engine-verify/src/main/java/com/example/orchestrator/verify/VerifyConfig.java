package com.example.orchestrator.verify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the Verifier.
 */
@ConfigurationProperties(prefix = "orchestrator.verify")
public class VerifyConfig {

    private String sandboxImage = "orchestrator/sandbox:1.0.0";
    private int defaultTimeoutSeconds = 300;
    private int maxParallelScanners = 4;
    private List<String> enabledScanners = List.of("compile", "test", "coverage", "spotbugs", "checkstyle", "semgrep", "depcheck", "trivy", "secrets");
    
    // Severity mapping from tool-specific to normalized (CRITICAL, HIGH, MEDIUM, LOW, INFO)
    private Map<String, Map<String, String>> severityMapping = Map.of(
        "spotbugs", Map.of(
            "HIGH", "HIGH",
            "MEDIUM", "MEDIUM", 
            "LOW", "LOW"
        ),
        "checkstyle", Map.of(
            "error", "HIGH",
            "warning", "MEDIUM",
            "info", "LOW"
        ),
        "semgrep", Map.of(
            "ERROR", "HIGH",
            "WARNING", "MEDIUM",
            "INFO", "LOW"
        ),
        "trivy", Map.of(
            "CRITICAL", "CRITICAL",
            "HIGH", "HIGH",
            "MEDIUM", "MEDIUM",
            "LOW", "LOW"
        ),
        "depcheck", Map.of(
            "HIGH", "HIGH",
            "MEDIUM", "MEDIUM",
            "LOW", "LOW"
        ),
        "secrets", Map.of(
            "CRITICAL", "CRITICAL",
            "HIGH", "HIGH"
        )
    );

    public String getSandboxImage() { return sandboxImage; }
    public void setSandboxImage(String sandboxImage) { this.sandboxImage = sandboxImage; }
    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public int getMaxParallelScanners() { return maxParallelScanners; }
    public void setMaxParallelScanners(int maxParallelScanners) { this.maxParallelScanners = maxParallelScanners; }
    public List<String> getEnabledScanners() { return enabledScanners; }
    public void setEnabledScanners(List<String> enabledScanners) { this.enabledScanners = enabledScanners; }
    public Map<String, Map<String, String>> getSeverityMapping() { return severityMapping; }
    public void setSeverityMapping(Map<String, Map<String, String>> severityMapping) { this.severityMapping = severityMapping; }
}
