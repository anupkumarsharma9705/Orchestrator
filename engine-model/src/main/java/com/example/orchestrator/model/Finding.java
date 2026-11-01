package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;

/**
 * A single finding from a verification tool.
 * 
 * @param fingerprint Unique fingerprint: SHA256(tool|ruleId|filePath|trimmedCodeLine)
 * @param tool Tool that produced this finding (e.g., "semgrep", "spotbugs", "checkstyle")
 * @param ruleId Rule identifier from the tool (e.g., "java.sql-injection")
 * @param severity Severity level
 * @param file Relative file path where finding was detected
 * @param line Line number (0 if file-level)
 * @param message Human-readable description
 * @param firstSeenRound Round number where first detected
 * @param fixedInRound Round number where fixed (null if still open)
 * @param status Current status
 * @param createdAt Timestamp when created
 */
public record Finding(
    @NotBlank String fingerprint,
    @NotBlank String tool,
    @NotBlank String ruleId,
    @NotNull Severity severity,
    String file,
    int line,
    @NotBlank String message,
    int firstSeenRound,
    Integer fixedInRound,
    @NotNull FindingStatus status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    /**
     * Checks if this finding is still open.
     */
    @JsonIgnore
    public boolean isOpen() {
        return status == FindingStatus.OPEN;
    }

    /**
     * Checks if this finding has been fixed.
     */
    @JsonIgnore
    public boolean isFixed() {
        return status == FindingStatus.FIXED;
    }

    /**
     * Checks if this finding is a regression (was fixed but reappeared).
     */
    @JsonIgnore
    public boolean isRegression() {
        return status == FindingStatus.REGRESSED;
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