package com.example.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A fix applied to address a finding.
 * 
 * @param findingFingerprint Fingerprint of the finding being fixed
 * @param round Round number when this fix was applied
 * @param summary Human-readable summary of the fix
 * @param filesChanged List of file paths that were modified
 * @param result Result of the fix attempt
 * @param appliedAt Timestamp when fix was applied
 */
public record Fix(
    @NotBlank String findingFingerprint,
    int round,
    @NotBlank String summary,
    @Size(min = 1) List<String> filesChanged,
    @NotNull FixResult result,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant appliedAt
) {
    public Fix {
        filesChanged = List.copyOf(filesChanged);
    }

    /**
     * Checks if the fix was successful.
     */
    @JsonIgnore
    public boolean isSuccessful() {
        return result == FixResult.VERIFIED_FIXED;
    }

    /**
     * Checks if the fix was reverted.
     */
    @JsonIgnore
    public boolean isReverted() {
        return result == FixResult.REVERTED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fix that)) return false;
        return Objects.equals(findingFingerprint, that.findingFingerprint) &&
               round == that.round;
    }

    @Override
    public int hashCode() {
        return Objects.hash(findingFingerprint, round);
    }
}