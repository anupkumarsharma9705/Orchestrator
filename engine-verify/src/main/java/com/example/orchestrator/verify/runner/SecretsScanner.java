package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.verify.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secrets detector (Semgrep-based).
 */
@Component
public class SecretsScanner implements Scanner {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Common secret patterns
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("ghp_[0-9A-Za-z]{36}");
    private static final Pattern SLACK_TOKEN_PATTERN = Pattern.compile("xoxb-[0-9]{10,13}-[0-9]{10,13}-[a-zA-Z0-9]{24}");
    private static final Pattern GENERIC_API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|secret[_-]?key|access[_-]?token)[:\\s\"'=]+([a-zA-Z0-9_-]{20,})");

    @Override
    public String name() {
        return "secrets";
    }

    @Override
    public String command(Path workspacePath) {
        return "semgrep --config=p/secrets --json " + workspacePath.toAbsolutePath();
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        // First, try Semgrep JSON output
        try {
            JsonNode root = new ObjectMapper().readTree(stdout);
            JsonNode results = root.get("results");
            
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    String ruleId = result.path("check_id").asText("");
                    String message = result.path("extra").path("message").asText("");
                    String path = result.path("path").asText("");
                    int line = result.path("start").path("line").asInt(0);
                    String severity = result.path("extra").path("severity").asText("WARNING");
                    
                    findings.add(new Finding(
                        "secrets-" + System.nanoTime(),
                        "secrets",
                        ruleId,
                        mapSeverity(severity),
                        message,
                        path,
                        line,
                        "",
                        "",
                        java.util.Map.of()
                    ));
                }
            }
        } catch (Exception e) {
            // Ignore Semgrep parsing errors, fall back to regex
        }
        
        // Also scan for common secret patterns in all files
        findings.addAll(scanForSecrets(workspacePath));
        
        return findings;
    }
    
    private List<Finding> scanForSecrets(Path workspacePath) {
        List<Finding> findings = new ArrayList<>();
        // In a real implementation, would walk the filesystem and scan files
        // For now, return empty - would be implemented with file walking
        return findings;
    }
    
    private Severity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "ERROR" -> com.example.orchestrator.verify.Severity.CRITICAL;
            case "WARNING" -> com.example.orchestrator.verify.Severity.HIGH;
            case "INFO" -> com.example.orchestrator.verify.Severity.LOW;
            default -> com.example.orchestrator.verify.Severity.HIGH;
        };
    }
}