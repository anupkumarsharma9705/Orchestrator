package com.example.orchestrator.security;

import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SecurityEnforcer {

    private final SecurityPolicy securityPolicy;

    public SecurityEnforcer(SecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
    }

    /**
     * Validates all generated code in tool results before they're applied
     */
    public SecurityPolicy.SecurityValidationResult validateToolResults(List<ToolResult> toolResults) {
        SecurityPolicy.SecurityValidationResult aggregate = new SecurityPolicy.SecurityValidationResult();
        
        for (ToolResult result : toolResults) {
            if (!result.success()) continue;
            
            // Check if output contains code that needs validation
            String output = result.output();
            if (output != null && looksLikeCode(output)) {
                SecurityPolicy.SecurityValidationResult validation = securityPolicy.validate(output, result.toolName());
                if (!validation.passed()) {
                    // Add all violations to aggregate
                    for (SecurityPolicy.SecurityViolation violation : validation.violations()) {
                        aggregate.addViolation(violation);
                    }
                }
            }
        }
        
        return aggregate;
    }

    /**
     * Validates a single file's content before writing
     */
    public SecurityPolicy.SecurityValidationResult validateFileContent(String content, String filePath) {
        return securityPolicy.validate(content, filePath);
    }

    /**
     * Enhances a generation prompt with security requirements
     */
    public String enhancePromptWithSecurity(String originalPrompt) {
        StringBuilder sb = new StringBuilder(originalPrompt);
        sb.append("\n\n");
        sb.append("SECURITY REQUIREMENTS (MANDATORY):\n");
        
        for (String practice : securityPolicy.getRequiredPractices()) {
            sb.append("- ").append(practice).append("\n");
        }
        
        sb.append("\nFORBIDDEN (will cause rejection):\n");
        sb.append("- String concatenation in SQL queries (use PreparedStatement)\n");
        sb.append("- Hardcoded secrets, passwords, or API keys\n");
        sb.append("- MD5, SHA1, DES, RC4, ECB mode encryption\n");
        sb.append("- Runtime.exec or ProcessBuilder with user input\n");
        sb.append("- Path traversal (../ in file paths)\n");
        sb.append("- ObjectInputStream deserialization\n");
        sb.append("- MD5, SHA1, DES, RC4 algorithms\n");
        sb.append("- Math.random() or new Random() for security tokens\n");
        sb.append("- Logging passwords, tokens, or PII\n");
        sb.append("- Hardcoded secrets in code\n");
        sb.append("- HTTP (non-HTTPS) external calls\n");
        sb.append("- Reflection with user-controlled input\n");
        
        sb.append("\nCODE WILL BE REJECTED IF IT VIOLATES THESE RULES.\n");
        
        return sb.toString();
    }

    private boolean looksLikeCode(String text) {
        return text != null && (
            text.contains("class ") ||
            text.contains("public ") ||
            text.contains("private ") ||
            text.contains("@RestController") ||
            text.contains("@Service") ||
            text.contains("@Repository") ||
            text.contains("@Entity") ||
            text.contains("public class") ||
            text.contains("interface ") ||
            text.contains("import ") ||
            text.contains("package ")
        );
    }
}