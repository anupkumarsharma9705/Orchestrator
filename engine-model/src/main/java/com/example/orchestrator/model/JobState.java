package com.example.orchestrator.model;

/**
 * State of a job in the orchestration pipeline.
 */
public enum JobState {
    /**
     * Job has been created but not started.
     */
    CREATED,

    /**
     * Job is in the clarification phase (asking questions).
     */
    CLARIFYING,

    /**
     * Requirements have been gathered and documented.
     */
    REQUIREMENTS_READY,

    /**
     * Prompt has been refined with all sections.
     */
    PROMPT_REFINED,

    /**
     * Task plan has been generated.
     */
    PLANNED,

    /**
     * Waiting for human approval of the plan.
     */
    AWAITING_APPROVAL,

    /**
     * Building the project (generating code).
     */
    BUILDING,

    /**
     * Running verification (tests, scanners).
     */
    VERIFYING,

    /**
     * Running fix loop rounds.
     */
    FIXING,

    /**
     * Job completed successfully.
     */
    COMPLETED,

    /**
     * Job failed.
     */
    FAILED,

    /**
     * Job was cancelled by user.
     */
    CANCELLED
}