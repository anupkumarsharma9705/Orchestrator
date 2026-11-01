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

/**
 * Semgrep scanner.
 */
@Component
public class SemgrepScanner implements Scanner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "semgrep";
    }

    @Override
    public String command(Path workspacePath) {
        return "semgrep --config=auto --json " + workspacePath.toAbsolutePath();
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(stdout);
            JsonNode results = root.get("results");
            
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    String ruleId = result.path("check_id").asText("");
                    String message = result.path("extra").path("message").asText("");
                    String path = result.path("path").asText("");
                    int line = result.path("start").path("line").asInt(0);
                    String severity = result.path("extra").path("severity").asText("WARNING");
                    
                    findings.add(new Finding(
                        "semgrep-" + System.nanoTime(),
                        "semgrep",
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
            // Ignore parsing errors
        }
        
        return findings;
    }
    
    private Severity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "ERROR" -> com.example.orchestrator.verify.Severity.HIGH;
            case "WARNING" -> com.example.orchestrator.verify.Severity.MEDIUM;
            case "INFO" -> com.example.orchestrator.verify.Severity.LOW;
            default -> com.example.orchestrator.verify.Severity.LOW;
        };
    }
}