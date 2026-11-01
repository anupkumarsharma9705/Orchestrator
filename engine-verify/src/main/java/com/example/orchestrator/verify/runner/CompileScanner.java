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
 * Maven compile scanner.
 */
@Component
public class CompileScanner implements Scanner {

    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+(.+?):\\[(\\d+),(\\d+)\\]\\s+(.+)"
    );

    @Override
    public String name() {
        return "compile";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn -q compile";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        String combined = stdout + "\n" + stderr;
        
        Matcher matcher = ERROR_PATTERN.matcher(combined);
        while (matcher.find()) {
            String filePath = matcher.group(1);
            int line = Integer.parseInt(matcher.group(2));
            int column = Integer.parseInt(matcher.group(3));
            String message = matcher.group(4);
            
            // Make path relative to workspace
            String relativePath = relativizePath(workspacePath, filePath);
            
            findings.add(new Finding(
                "compile-" + System.nanoTime(),
                "compile",
                "COMPILE_ERROR",
                Severity.HIGH,
                message,
                relativePath,
                line,
                "",
                "",
                java.util.Map.of("column", column)
            ));
        }
        
        return findings;
    }

    private String relativizePath(Path workspacePath, String filePath) {
        try {
            Path path = Path.of(filePath).normalize();
            Path workspace = workspacePath.normalize();
            if (path.startsWith(workspace)) {
                return workspace.relativize(path).toString();
            }
        } catch (Exception e) {
            // Ignore
        }
        return filePath;
    }
}