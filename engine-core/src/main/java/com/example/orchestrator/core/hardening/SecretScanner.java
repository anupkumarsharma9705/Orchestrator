package com.example.orchestrator.core.hardening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans prompts and text for potential secrets (API keys, passwords, tokens, etc.).
 */
@Component
public class SecretScanner {

    private static final Logger log = LoggerFactory.getLogger(SecretScanner.class);

    // Secret patterns with names for reporting
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
        new SecretPattern("AWS Access Key", Pattern.compile("AKIA[0-9A-Z]{16}")),
        new SecretPattern("AWS Secret Key", Pattern.compile("(?i)aws[_-]?secret[_-]?access[_-]?key[\\s\"':=]+[A-Za-z0-9/+=]{40}")),
        new SecretPattern("GitHub Token", Pattern.compile("ghp_[A-Za-z0-9]{36}")),
        new SecretPattern("GitHub OAuth Token", Pattern.compile("gho_[A-Za-z0-9]{36}")),
        new SecretPattern("GitHub App Token", Pattern.compile("ghs_[A-Za-z0-9]{36}")),
        new SecretPattern("GitHub Refresh Token", Pattern.compile("ghr_[A-Za-z0-9]{36}")),
        new SecretPattern("Slack Bot Token", Pattern.compile("xoxb-[0-9]{10,13}-[0-9]{10,13}-[A-Za-z0-9]{24}")),
        new SecretPattern("Slack User Token", Pattern.compile("xoxp-[0-9]{10,13}-[0-9]{10,13}-[0-9]{10,13}-[A-Za-z0-9]{24}")),
        new SecretPattern("Slack App Token", Pattern.compile("xapp-[0-9]{10,13}-[0-9]{10,13}-[A-Za-z0-9]{24}")),
        new SecretPattern("Stripe Secret Key", Pattern.compile("sk_live_[A-Za-z0-9]{24}")),
        new SecretPattern("Stripe Publishable Key", Pattern.compile("pk_live_[A-Za-z0-9]{24}")),
        new SecretPattern("Stripe Test Secret", Pattern.compile("sk_test_[A-Za-z0-9]{24}")),
        new SecretPattern("Stripe Test Publishable", Pattern.compile("pk_test_[A-Za-z0-9]{24}")),
        new SecretPattern("Google API Key", Pattern.compile("AIza[A-Za-z0-9_-]{35}")),
        new SecretPattern("Google OAuth Client Secret", Pattern.compile("GOCSPX-[A-Za-z0-9_-]{28}")),
        new SecretPattern("JWT Token", Pattern.compile("eyJ[A-Za-z0-9_-]*\\.eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*")),
        new SecretPattern("Bearer Token", Pattern.compile("(?i)bearer\\s+[A-Za-z0-9_-]{20,}")),
        new SecretPattern("API Key (Generic)", Pattern.compile("(?i)(api[_-]?key|apikey)[\\s\"':=]+[A-Za-z0-9_-]{20,}")),
        new SecretPattern("Secret Key (Generic)", Pattern.compile("(?i)(secret[_-]?key|secretkey)[\\s\"':=]+[A-Za-z0-9/+=]{20,}")),
        new SecretPattern("Access Token (Generic)", Pattern.compile("(?i)access[_-]?token[\\s\"':=]+[A-Za-z0-9_-]{20,}")),
        new SecretPattern("Private Key (PEM)", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----")),
        new SecretPattern("SSH Private Key", Pattern.compile("-----BEGIN OPENSSH PRIVATE KEY-----")),
        new SecretPattern("Database URL with Password", Pattern.compile("(?i)(jdbc|mongodb|postgres|mysql|redis)://[^:]+:[^@]+@")),
        // Database URL without password (for tracking)
        new SecretPattern("Database URL (no password)", Pattern.compile("(?i)(jdbc|mongodb|postgres|mysql|redis)://[^:@/]+@")),
        new SecretPattern("Connection String", Pattern.compile("(?i)(connectionstring|connection_string)[\\s\"':=]+[^\\s;]{20,}")),
        new SecretPattern("Auth Token", Pattern.compile("(?i)auth[_-]?token[\\s\"':=]+[A-Za-z0-9_-]{20,}")),
        new SecretPattern("Session ID", Pattern.compile("(?i)session[_-]?id[\\s\"':=]+[A-Za-z0-9_-]{20,}")),
        new SecretPattern("Encryption Key", Pattern.compile("(?i)encryption[_-]?key[\\s\"':=]+[A-Za-z0-9/+=]{20,}")),
        new SecretPattern("Signing Key", Pattern.compile("(?i)signing[_-]?key[\\s\"':=]+[A-Za-z0-9/+=]{20,}")),
        // Additional patterns for H4
        new SecretPattern("Hex Encoded Secret", Pattern.compile("(?i)[0-9a-f]{64,}")),
        new SecretPattern("Base64URL Encoded", Pattern.compile("[A-Za-z0-9_-]{43,}=")),
        new SecretPattern("Private Key (OpenSSH)", Pattern.compile("-----BEGIN OPENSSH PRIVATE KEY-----")),
        new SecretPattern("PGP Private Key", Pattern.compile("-----BEGIN PGP PRIVATE KEY BLOCK-----"))
    );

    // High entropy patterns (fallback for unknown secret formats)
    // Fixed ReDoS vulnerability: using possessive quantifier and atomic group to prevent catastrophic backtracking
    private static final Pattern HIGH_ENTROPY_PATTERN = Pattern.compile(
        "(?>[A-Za-z0-9/+=]{40,})" // Base64-like strings 40+ chars, atomic group prevents backtracking
    );

    /**
     * Result of a secret scan.
     */
    public record ScanResult(
        boolean hasSecrets,
        List<FoundSecret> foundSecrets,
        int totalMatches
    ) {}

    /**
     * A found secret with location info.
     * matchedText is REDACTED to prevent secret leakage in logs/storage.
     */
    public record FoundSecret(
        String type,
        String matchedText, // Always redacted - never contains actual secret
        int startIndex,
        int endIndex,
        String context
    ) {}

    /**
     * Creates a redacted version of the matched secret for safe logging/storage.
     */
    private String redactMatchedText(String type, String matched) {
        // Show only first 4 chars and type for identification, never the full secret
        String prefix = matched.length() > 4 ? matched.substring(0, 4) : "****";
        return String.format("[REDACTED:%s:%s****]", type, prefix);
    }

    /**
     * Scans text for secrets.
     * 
     * @param text Text to scan
     * @return ScanResult with found secrets
     */
    public ScanResult scan(String text) {
        if (text == null || text.isBlank()) {
            return new ScanResult(false, List.of(), 0);
        }

        List<FoundSecret> found = new ArrayList<>();

        // Check known secret patterns
        for (SecretPattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.pattern().matcher(text);
            while (matcher.find()) {
                String matched = matcher.group();
                // Skip if it looks like a placeholder or example
                if (isPlaceholder(matched)) {
                    continue;
                }
                
                int start = matcher.start();
                int end = matcher.end();
                int contextStart = Math.max(0, start - 50);
                int contextEnd = Math.min(text.length(), end + 50);
                String context = text.substring(contextStart, contextEnd);

                found.add(new FoundSecret(
                    pattern.name(),
                    redactMatchedText(pattern.name(), matched),
                    start,
                    end,
                    context
                ));
            }
        }

        // Check for high entropy strings (potential unknown secrets)
        if (found.isEmpty()) {
            Matcher entropyMatcher = HIGH_ENTROPY_PATTERN.matcher(text);
            while (entropyMatcher.find()) {
                String matched = entropyMatcher.group();
                if (isPlaceholder(matched) || isCommonFalsePositive(matched)) {
                    continue;
                }
                
                // Only flag if it looks like a secret (not common words)
                if (looksLikeSecret(matched)) {
                    int start = entropyMatcher.start();
                    int end = entropyMatcher.end();
                    int contextStart = Math.max(0, start - 50);
                    int contextEnd = Math.min(text.length(), end + 50);
                    String context = text.substring(contextStart, contextEnd);

                    found.add(new FoundSecret(
                        "High Entropy String",
                        redactMatchedText("High Entropy String", matched),
                        start,
                        end,
                        context
                    ));
                }
            }
        }

        boolean hasSecrets = !found.isEmpty();
        if (hasSecrets) {
            log.warn("Secrets detected in text: {} secret(s) found", found.size());
            for (FoundSecret secret : found) {
                log.debug("  - {}: {} (context: {})", 
                    secret.type(), 
                    secret.matchedText(),
                    secret.context().replace("\n", " "));
            }
        }

        return new ScanResult(hasSecrets, found, found.size());
    }

    /**
     * Scans and redacts secrets from text.
     * 
     * @param text Text to sanitize
     * @return Text with secrets replaced by [REDACTED]
     */
    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        ScanResult result = scan(text);
        if (!result.hasSecrets()) {
            return text;
        }

        StringBuilder redacted = new StringBuilder(text);
        // Process in reverse order to maintain indices
        List<FoundSecret> sorted = new ArrayList<>(result.foundSecrets());
        sorted.sort((a, b) -> Integer.compare(b.startIndex(), a.startIndex()));

        for (FoundSecret secret : sorted) {
            redacted.replace(secret.startIndex(), secret.endIndex(), 
                "[REDACTED: " + secret.type() + "]");
        }

        return redacted.toString();
    }

    /**
     * Checks if a matched string is likely a placeholder/example.
     */
    private boolean isPlaceholder(String matched) {
        String lower = matched.toLowerCase();
        return lower.contains("example") 
            || lower.contains("placeholder")
            || lower.contains("test")
            || lower.contains("demo")
            || lower.contains("sample")
            || lower.contains("your_key")
            || lower.contains("your_token")
            || lower.contains("your_secret")
            || lower.contains("xxx")
            || lower.contains("...")
            || matched.equals("AKIAAAAAAAAAAAAAAAAA")
            || matched.matches(".*[0]{10,}.*"); // Too many zeros
    }

    /**
     * Checks for common false positives in high entropy strings.
     */
    private boolean isCommonFalsePositive(String matched) {
        // Common words that happen to be high entropy - use word boundaries to avoid partial matches
        String lower = matched.toLowerCase();
        return lower.matches(".*\\b(the|and|for|with|this|that|from|have|been|were|will|would|could|should|their|there|where|when|what|which|while|after|before|during|through|between|under|above|below|into|onto|upon|since|until|unless|because|although|though|even|only|just|also|both|each|every|other|another|such|same|different|various|several|many|few|more|most|less|least|very|too|so|as|if|then|else|when|while|do|does|did|done|can|could|may|might|must|shall|should|will|would|has|had|have|having|is|are|was|were|be|been|being|am|i|you|he|she|it|we|they|me|him|her|us|them|my|your|his|her|its|our|their|mine|yours|hers|ours|theirs)\\b.*");
    }

    /**
     * Heuristic to determine if a high-entropy string looks like a secret.
     */
    private boolean looksLikeSecret(String matched) {
        // Must have mixed case or numbers + special chars
        boolean hasUpper = matched.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = matched.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = matched.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = matched.chars().anyMatch(c -> "+/=".indexOf(c) >= 0);
        
        return (hasUpper && hasLower && hasDigit) || (hasSpecial && (hasUpper || hasLower));
    }

    /**
     * Internal record for secret pattern with name.
     */
    private record SecretPattern(String name, Pattern pattern) {}
}