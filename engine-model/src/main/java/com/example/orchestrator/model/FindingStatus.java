package com.example.orchestrator.model;

/**
 * Status of a finding across fix rounds.
 */
public enum FindingStatus {
    /**
     * Finding is open and needs to be fixed.
     */
    OPEN,

    /**
     * Finding has been fixed and verified.
     */
    FIXED,

    /**
     * Finding was fixed but reappeared (regression).
     */
    REGRESSED,

    /**
     * Finding was acknowledged as acceptable risk.
     */
    ACCEPTED
}