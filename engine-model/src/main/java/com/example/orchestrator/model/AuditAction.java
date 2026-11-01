package com.example.orchestrator.model;

/**
 * Actions in the approval audit trail.
 */
public enum AuditAction {
    /**
     * Approval was requested.
     */
    REQUESTED,

    /**
     * Approval was granted.
     */
    APPROVED,

    /**
     * Approval was rejected.
     */
    REJECTED,

    /**
     * Approval timed out.
     */
    TIMEOUT,

    /**
     * Approval was cancelled.
     */
    CANCELLED
}