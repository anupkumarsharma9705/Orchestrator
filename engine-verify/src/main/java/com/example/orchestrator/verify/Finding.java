package com.example.orchestrator.verify;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Objects;

/**
 * A finding from a verification scanner.
 * 
 * @param id Unique identifier for this finding
 * @param tool The scanner tool that produced this finding (e.g., "spotbugs", "checkstyle")
 * @param ruleId The rule ID from the tool
 * @param severity Normalized severity level
 * @param message Human-readable description
 * @param filePath Relative path to the file
 * @param lineNumber Line number in the file (0 if not applicable)
 * @param codeSnippet Relevant code snippet
 * @param fingerprint Unique fingerprint for deduplication
 * @param metadata Additional tool-specific metadata
 */
public record Finding(
    String id,
    String tool,
    String ruleId,
    Severity severity,
    String message,
    String filePath,
    int lineNumber,
    String codeSnippet,
    String fingerprint,
    Map<String, Object> metadata
) {
    public Finding {
        if (fingerprint == null || fingerprint.isBlank()) {
            fingerprint = computeFingerprint(tool, ruleId, filePath, lineNumber, codeSnippet);
        }
    }

    @JsonIgnore
    public static String computeFingerprint(String tool, String ruleId, String filePath, int lineNumber, String codeSnippet) {
        String input = tool + "|" + ruleId + "|" + filePath + "|" + lineNumber + "|" + 
            (codeSnippet != null ? codeSnippet.trim() : "");
        try {
            return java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes())
            ).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Finding that)) return false;
        return Objects.equals(fingerprint, that.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint);
    }
}