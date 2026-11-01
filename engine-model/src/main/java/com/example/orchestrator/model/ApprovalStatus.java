package com.example.orchestrator.model;

/**
 * Status of a plan approval.
 */
public enum ApprovalStatus {
    /**
     * Approval is pending.
     */
    PENDING,

    /**
     * Plan was approved.
     */
    APPROVED,

    /**
     * Plan was rejected.
     */
    REJECTED,

    /**
     * Approval timed out.
     */
    TIMEOUT
}