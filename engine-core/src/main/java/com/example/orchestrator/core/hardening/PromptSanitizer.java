package com.example.orchestrator.core.hardening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes prompts to defend against prompt injection attacks.
 * Strips instruction-like text, role manipulation attempts, and other malicious patterns.
 */
@Component
public class PromptSanitizer {

    private static final Logger log = LoggerFactory.getLogger(PromptSanitizer.class);

    // Patterns that indicate prompt injection attempts
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        // Role manipulation
        Pattern.compile("(?i)(ignore|forget|disregard)\\s+(previous|above|all)\\s+(instructions|prompts|rules)"),
        Pattern.compile("(?i)you\\s+are\\s+(now|now a|an?)\\s+"),
        Pattern.compile("(?i)(act|pretend|roleplay)\\s+as\\s+(an?|a)\\s+"),
        
        // Instruction override
        Pattern.compile("(?i)(new|updated|changed)\\s+(instructions|rules|guidelines)"),
        Pattern.compile("(?i)ignore\\s+(the\\s+)?(system|user|assistant)\\s+(prompt|message|instruction)"),
        Pattern.compile("(?i)do\\s+not\\s+(follow|obey|listen)\\s+(to\\s+)?(the\\s+)?(previous|above)"),
        
        // Data exfiltration
        Pattern.compile("(?i)(print|output|show|display|reveal)\\s+(the\\s+)?(system|hidden|secret|internal)\\s+(prompt|instruction|context)"),
        Pattern.compile("(?i)what\\s+(is|was)\\s+(your|the)\\s+(system|initial|original)\\s+(prompt|instruction)"),
        
        // Chain of thought manipulation
        Pattern.compile("(?i)think\\s+step\\s+by\\s+step\\s*(and\\s+then)?\\s*(ignore|forget|disregard)"),
        Pattern.compile("(?i)let'?s\\s+(think|reason)\\s+(about|through)\\s+(this|it)\\s+(carefully|step by step)"),
        
        // Delimiter confusion
        Pattern.compile("(?i)end\\s+(of|the)\\s+(prompt|instruction|context)"),
        Pattern.compile("(?i)(---|===|\\*\\*\\*)\\s*(end|stop|finish)"),
        
        // Encoding attempts
        Pattern.compile("(?i)(base64|rot13|hex|url)\\s*(encode|decode)"),
        
        // Jailbreak patterns
        Pattern.compile("(?i)(DAN|Do Anything Now|unrestricted|unfiltered|no rules)"),
        Pattern.compile("(?i)(developer|admin|root|sudo)\\s+mode"),
        
        // Recursive/self-reference
        Pattern.compile("(?i)this\\s+(prompt|message|instruction)\\s+(contains|includes|has)\\s+(instructions|rules)")
    );

    // Patterns that are allowed (legitimate uses)
    private static final List<Pattern> ALLOWED_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)step\\s+by\\s+step"), // Legitimate reasoning request
        Pattern.compile("(?i)think\\s+carefully"),  // Legitimate reasoning request
        Pattern.compile("(?i)explain\\s+your\\s+reasoning") // Legitimate explanation request
    );

    /**
     * Sanitizes a prompt by removing potential injection patterns.
     * 
     * Fixes:
     * - Applies all patterns to original text (not sequential modified text) to avoid order dependency
     * - Normalizes Unicode (NFC) before scanning to prevent Unicode bypass
     * - Enforces maximum input length (100KB) to prevent DoS
     * - Uses a single-pass approach with StringBuilder for efficiency
     * 
     * @param prompt The prompt to sanitize
     * @return Sanitized prompt
     */
    public String sanitize(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return prompt;
        }

        // H5: Enforce maximum input length to prevent DoS
        if (prompt.length() > 100 * 1024) {
            log.warn("Prompt exceeds maximum length (100KB), rejecting: {} chars", prompt.length());
            throw new IllegalArgumentException("Prompt too long: maximum 100KB");
        }

        // H5: Normalize Unicode (NFC) to prevent Unicode bypass attacks
        String normalized = Normalizer.normalize(prompt, Normalizer.Form.NFC);

        // Track all matches across all patterns before applying any replacements
        List<MatchResult> allMatches = new ArrayList<>();

        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) {
                String matched = matcher.group();
                
                // Check if it's an allowed pattern
                boolean isAllowed = ALLOWED_PATTERNS.stream()
                    .anyMatch(p -> p.matcher(matched).find());
                
                if (!isAllowed) {
                    allMatches.add(new MatchResult(matcher.start(), matcher.end(), matched, pattern));
                }
            }
        }

        if (allMatches.isEmpty()) {
            return normalized;
        }

        // Sort matches by start position (descending) for safe replacement
        allMatches.sort((a, b) -> Integer.compare(b.start(), a.start()));

        // Apply replacements from end to beginning to maintain correct indices
        StringBuilder result = new StringBuilder(normalized);
        for (MatchResult match : allMatches) {
            log.warn("Prompt injection pattern detected and removed: {}", match.matched());
            result.replace(match.start(), match.end(), "[REDACTED: PROMPT INJECTION]");
        }

        String sanitized = result.toString();
        log.info("Prompt sanitized. Original length: {}, Sanitized length: {}, Patterns matched: {}", 
            prompt.length(), sanitized.length(), allMatches.size());

        return sanitized;
    }

    /**
     * Internal record for tracking pattern matches before replacement.
     */
    private record MatchResult(int start, int end, String matched, Pattern pattern) {}

    /**
     * Checks if a prompt contains potential injection patterns without modifying it.
     * Applies Unicode NFC normalization to prevent Unicode bypass attacks.
     * 
     * @param prompt The prompt to check
     * @return true if injection patterns detected
     */
    public boolean hasInjection(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }

        // Apply Unicode NFC normalization to prevent Unicode bypass attacks
        String normalized = Normalizer.normalize(prompt, Normalizer.Form.NFC);

        return INJECTION_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * Sanitizes a map of prompt variables (e.g., template variables).
     * 
     * @param variables Map of variable names to values
     * @return Sanitized map
     */
    public java.util.Map<String, String> sanitizeVariables(java.util.Map<String, String> variables) {
        if (variables == null) {
            return variables;
        }

        java.util.Map<String, String> sanitized = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            sanitized.put(entry.getKey(), sanitize(entry.getValue()));
        }
        return sanitized;
    }
}