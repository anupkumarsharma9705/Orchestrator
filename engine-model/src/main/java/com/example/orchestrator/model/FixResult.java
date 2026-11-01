package com.example.orchestrator.model;

/**
 * Result of a fix attempt.
 */
public enum FixResult {
    /**
     * Fix was verified as correct.
     */
    VERIFIED_FIXED,

    /**
     * Fix was reverted due to regression or test failure.
     */
    REVERTED,

    /**
     * Fix was accepted as-is (e.g., false positive acknowledged).
     */
    ACCEPTED,

    /**
     * Fix attempt failed.
     */
    FAILED
}