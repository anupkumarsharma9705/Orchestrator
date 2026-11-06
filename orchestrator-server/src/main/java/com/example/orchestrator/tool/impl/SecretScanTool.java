package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.Tool;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

@Component
public class SecretScanTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SecretScanTool.class);
    
    private final ObjectMapper objectMapper;
    
    // M8: ReDoS protection - max regex match time per line (ms)
    private static final long MAX_REGEX_TIME_MS = 100;
    
    // Common secret patterns - ReDoS resistant
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
        // AWS Access Key - fixed length, no backtracking
        new SecretPattern("AWS Access Key", Pattern.compile("AKIA[0-9A-Z]{16}")),
        // AWS Secret Key - use possessive quantifiers and limit scope
        new SecretPattern("AWS Secret Key", Pattern.compile("(?i)aws_secret_access_key\\s*+[=:]\\s*+[A-Za-z0-9/+=]{40}")),
        // GitHub Token - fixed prefix, limited length
        new SecretPattern("GitHub Token", Pattern.compile("gh[pousr]_[A-Za-z0-9_]{36,255}")),
        // GitLab Token - fixed prefix
        new SecretPattern("GitLab Token", Pattern.compile("glpat-[A-Za-z0-9_-]{20,}")),
        // Slack Token - fixed prefix
        new SecretPattern("Slack Token", Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}")),
        // Generic API Key - use possessive quantifiers
        new SecretPattern("Generic API Key", Pattern.compile("(?i)(api[_-]?key|apikey)\\s*+[=:]\\s*+[A-Za-z0-9_-]{20,}")),
        // Private Key - fixed strings, no backtracking
        new SecretPattern("Private Key", Pattern.compile("-----BEGIN (RSA|DSA|EC|OPENSSH) PRIVATE KEY-----")),
        // JWT Token - use possessive quantifiers for each segment
        new SecretPattern("JWT Token", Pattern.compile("eyJ[A-Za-z0-9_-]*+\\.[A-Za-z0-9_-]*+\\.[A-Za-z0-9_-]*+")),
        // Database URL with Password - use possessive quantifiers
        new SecretPattern("Database URL with Password", Pattern.compile("(?i)(jdbc|mongodb|redis|postgres|mysql)://[^:]+:[^@]+@")),
        // Docker Config Auth - fixed format
        new SecretPattern("Docker Config Auth", Pattern.compile("\"auth\"\\s*:\\s*\"[A-Za-z0-9+/=]+\"")),
        // Generic Secret - use possessive quantifiers, limit to reasonable length
        new SecretPattern("Generic Secret", Pattern.compile("(?i)(secret|password|passwd|pwd|token|key)\\s*+[=:]\\s*+[A-Za-z0-9/+=]{16,64}"))
    );

    public SecretScanTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
            "secretScan",
            "Scan for secrets and credentials in the codebase",
            ToolSpec.RiskLevel.CONTROLLED,
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "Relative path from project root (default: .)" },
                "timeoutSeconds": { "type": "integer", "description": "Timeout in seconds (default: 60)" }
              },
              "required": []
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "exitCode": { "type": "integer" },
                "output": { "type": "string" },
                "success": { "type": "boolean" },
                "findings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": { "type": "string" },
                      "path": { "type": "string" },
                      "line": { "type": "integer" },
                      "match": { "type": "string" }
                    }
                  }
                }
              }
            }
            """
        );
    }

    @Override
    public ToolResult execute(Project project, String input) {
        long start = System.currentTimeMillis();
        try {
            SecretScanInput params = objectMapper.readValue(input, SecretScanInput.class);
            
            Path basePath = project.rootPath().toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(params.path != null ? params.path : ".").normalize();
            
            if (!targetPath.startsWith(basePath)) {
                return ToolResult.failure("secretScan", "Path traversal attempt denied", System.currentTimeMillis() - start);
            }
            
            if (!Files.exists(targetPath)) {
                return ToolResult.failure("secretScan", "Path not found: " + params.path, System.currentTimeMillis() - start);
            }
            
            int timeout = params.timeoutSeconds != null ? params.timeoutSeconds : 60;
            
            List<SecretFinding> findings = new ArrayList<>();
            
            // Scan files
            Files.walk(targetPath)
                .filter(Files::isRegularFile)
                .filter(p -> !shouldExclude(p, targetPath))
                .filter(p -> {
                    try {
                        return Files.size(p) < 1024 * 1024; // Skip files > 1MB
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        scanFile(path, targetPath, findings);
                    } catch (IOException ignored) {}
                });
            
            int exitCode = findings.isEmpty() ? 0 : 1;
            String output = objectMapper.writeValueAsString(new SecretScanOutput(exitCode, "", true, findings));
            
            return ToolResult.success("secretScan", output, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            return ToolResult.failure("secretScan", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void scanFile(Path file, Path basePath, List<SecretFinding> findings) throws IOException {
        String content = Files.readString(file);
        String relativePath = basePath.relativize(file).toString();
        String[] lines = content.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // M8: ReDoS protection - skip extremely long lines
            if (line.length() > 10000) continue;
            
            for (SecretPattern pattern : SECRET_PATTERNS) {
                // M8: ReDoS protection - the CharSequence enforces a hard time
                // budget during matching, not after the regex returns.
                long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_REGEX_TIME_MS);
                try {
                    Matcher matcher = pattern.pattern()
                        .matcher(new TimeBoundedCharSequence(line, deadlineNanos));
                    if (matcher.find()) {
                        // Mask the actual secret in the output
                        String maskedMatch = matcher.group().replaceAll("[A-Za-z0-9/+=]{8,}", "***REDACTED***");
                        findings.add(new SecretFinding(
                            pattern.name(),
                            basePath.relativize(file).toString(),
                            i + 1,
                            maskedMatch
                        ));
                    }
                } catch (TimeBoundedCharSequence.RegexTimeoutException e) {
                    log.warn("Regex timeout for pattern {} on line {} of {}", pattern.name(), i + 1, relativePath);
                } catch (Exception e) {
                    // Skip this pattern if it causes issues
                    continue;
                }
            }
        }
    }

    private boolean shouldExclude(Path path, Path basePath) {
        Path relative = basePath.relativize(path);
        for (Path element : relative) {
            String name = element.toString();
            if (Set.of(".git", "target", "build", "node_modules", "venv", ".venv", "__pycache__", ".idea", ".vscode", "out", "dist", "bin", "obj", "~").contains(name)) {
                return true;
            }
            if (name.startsWith(".") && !name.equals(".gitignore")) {
                return true;
            }
        }
        String fileName = path.getFileName().toString();
        for (String ext : Set.of(".class", ".jar", ".war", ".ear", ".exe", ".dll", ".so", ".dylib", ".log", ".tmp", ".cache", ".pid", ".lock", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot")) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private record SecretScanInput(String path, Integer timeoutSeconds) {}
    private record SecretScanOutput(int exitCode, String output, boolean success, List<SecretFinding> findings) {}
    private record SecretFinding(String type, String path, int line, String match) {}
    private record SecretPattern(String name, Pattern pattern) {}
}