package com.example.orchestrator.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing finding fingerprints.
 * Fingerprint = SHA256(tool + "|" + ruleId + "|" + filePath + "|" + trimmedCodeLine)
 * Survives line number changes and whitespace differences.
 */
public final class FingerprintUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final String DELIMITER = "|";

    private FingerprintUtil() {
        // Utility class
    }

    /**
     * Computes a fingerprint for a finding.
     * 
     * @param tool Tool name (e.g., "semgrep", "spotbugs")
     * @param ruleId Rule identifier (e.g., "java.sql-injection")
     * @param filePath Relative file path
     * @param codeLine The code line that triggered the finding
     * @return SHA-256 hash as hex string
     */
    public static String compute(String tool, String ruleId, String filePath, String codeLine) {
        String normalizedCodeLine = normalizeCodeLine(codeLine);
        String input = tool + DELIMITER + ruleId + DELIMITER + filePath + DELIMITER + normalizedCodeLine;
        return hash(input);
    }

    /**
     * Computes a fingerprint with explicit line number.
     * 
     * @param tool Tool name
     * @param ruleId Rule identifier
     * @param filePath Relative file path
     * @param lineNumber Line number
     * @param codeLine The code line
     * @return SHA-256 hash as hex string
     */
    public static String computeWithLine(String tool, String ruleId, String filePath, int lineNumber, String codeLine) {
        String normalizedCodeLine = normalizeCodeLine(codeLine);
        String input = tool + DELIMITER + ruleId + DELIMITER + filePath + DELIMITER + lineNumber + DELIMITER + normalizedCodeLine;
        return hash(input);
    }

    /**
     * Normalizes a code line for consistent fingerprinting.
     * - Trims leading/trailing whitespace
     * - Collapses multiple spaces to single space
     * - Removes trailing semicolons for comparison
     */
    private static String normalizeCodeLine(String codeLine) {
        if (codeLine == null) {
            return "";
        }
        return codeLine.trim()
            .replaceAll("\\s+", " ")
            .replaceAll(";$", "");
    }

    /**
     * Computes SHA-256 hash of input string.
     */
    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Converts byte array to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Verifies that a fingerprint matches the expected components.
     */
    public static boolean verify(String fingerprint, String tool, String ruleId, String filePath, String codeLine) {
        String computed = compute(tool, ruleId, filePath, codeLine);
        return fingerprint.equals(computed);
    }
}