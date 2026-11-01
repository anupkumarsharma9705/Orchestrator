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
 * Maven test scanner.
 */
@Component
public class TestScanner implements Scanner {

    private static final Pattern FAILURE_PATTERN = Pattern.compile(
        "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)"
    );
    private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(
        "<<< (FAILURE|ERROR)!\\s*\\n(.+?)\\n\\s*at (.+?)\\("
    );

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn test";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        String combined = stdout + "\n" + stderr;
        
        // Parse test summary
        Matcher summaryMatcher = FAILURE_PATTERN.matcher(combined);
        if (summaryMatcher.find()) {
            int failures = Integer.parseInt(summaryMatcher.group(2));
            int errors = Integer.parseInt(summaryMatcher.group(3));
            if (failures > 0 || errors > 0) {
                findings.add(new Finding(
                    "test-summary-" + System.nanoTime(),
                    "test",
                    "TEST_FAILURES",
                    Severity.HIGH,
                    "Tests failed: " + failures + " failures, " + errors + " errors",
                    "",
                    0,
                    "",
                    "",
                    java.util.Map.of("failures", failures, "errors", errors)
                ));
            }
        }
        
        // Parse individual test failures
        Matcher failureMatcher = TEST_FAILURE_PATTERN.matcher(combined);
        while (failureMatcher.find()) {
            String type = failureMatcher.group(1);
            String message = failureMatcher.group(2);
            String location = failureMatcher.group(3);
            
            findings.add(new Finding(
                "test-failure-" + System.nanoTime(),
                "test",
                "TEST_" + type,
                Severity.HIGH,
                message,
                extractFilePath(location),
                extractLineNumber(location),
                "",
                "",
                java.util.Map.of("location", location)
            ));
        }
        
        return findings;
    }
    
    private String extractFilePath(String location) {
        int lastDot = location.lastIndexOf('.');
        if (lastDot > 0) {
            String classPath = location.substring(0, lastDot).replace('.', '/');
            return classPath + ".java";
        }
        return "";
    }
    
    private int extractLineNumber(String location) {
        int paren = location.lastIndexOf('(');
        if (paren > 0) {
            String linePart = location.substring(paren + 1);
            int colon = linePart.indexOf(':');
            if (colon > 0) {
                try {
                    return Integer.parseInt(linePart.substring(0, colon));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return 0;
    }
}