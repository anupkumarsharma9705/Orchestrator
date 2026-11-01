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
 * Trivy vulnerability scanner.
 */
@Component
public class TrivyScanner implements Scanner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "trivy";
    }

    @Override
    public String command(Path workspacePath) {
        return "trivy fs --format json " + workspacePath.toAbsolutePath();
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        try {
            JsonNode root = new ObjectMapper().readTree(stdout);
            JsonNode results = root.get("Results");
            
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    JsonNode vulnerabilities = result.get("Vulnerabilities");
                    if (vulnerabilities != null && vulnerabilities.isArray()) {
                        for (JsonNode vuln : vulnerabilities) {
                            String vulnId = vuln.path("VulnerabilityID").asText("");
                            String pkgName = vuln.path("PkgName").asText("");
                            String installedVersion = vuln.path("InstalledVersion").asText("");
                            String fixedVersion = vuln.path("FixedVersion").asText("");
                            String severity = vuln.path("Severity").asText("UNKNOWN");
                            String description = vuln.path("Description").asText("");
                            
                            findings.add(new Finding(
                                "trivy-" + vulnId,
                                "trivy",
                                vulnId,
                                mapSeverity(severity),
                                String.format("%s in %s (%s), fixed in %s", description, pkgName, installedVersion, fixedVersion),
                                pkgName,
                                0,
                                "",
                                "",
                                java.util.Map.of(
                                    "installedVersion", installedVersion,
                                    "fixedVersion", fixedVersion,
                                    "package", pkgName
                                )
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return findings;
    }
    
    private Severity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> com.example.orchestrator.verify.Severity.CRITICAL;
            case "HIGH" -> com.example.orchestrator.verify.Severity.HIGH;
            case "MEDIUM" -> com.example.orchestrator.verify.Severity.MEDIUM;
            case "LOW" -> com.example.orchestrator.verify.Severity.LOW;
            default -> com.example.orchestrator.verify.Severity.LOW;
        };
    }
}