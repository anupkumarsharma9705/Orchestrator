package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.verify.Severity;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JaCoCo coverage scanner.
 */
@Component
public class CoverageScanner implements Scanner {

    private static final Pattern COVERAGE_PATTERN = Pattern.compile(
        "Total\\.+?(\\d+)%\\s*\\d+\\s*\\d+"
    );
    private static final Pattern LINE_COVERAGE_PATTERN = Pattern.compile(
        "Line\\s+(\\d+)%"
    );

    @Override
    public String name() {
        return "coverage";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn jacoco:report";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        String combined = stdout + "\n" + stderr;
        
        Matcher matcher = LINE_COVERAGE_PATTERN.matcher(combined);
        double coverage = 0.0;
        if (matcher.find()) {
            try {
                coverage = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Add coverage finding if below threshold
        if (coverage < 80.0) {
            findings.add(new Finding(
                "coverage-threshold-" + System.nanoTime(),
                "coverage",
                "COVERAGE_BELOW_THRESHOLD",
                Severity.MEDIUM,
                String.format("Code coverage %.1f%% is below 80%% threshold", coverage),
                "",
                0,
                "",
                "",
                java.util.Map.of("coverage", coverage, "threshold", 80.0)
            ));
        }
        
        return findings;
    }
}