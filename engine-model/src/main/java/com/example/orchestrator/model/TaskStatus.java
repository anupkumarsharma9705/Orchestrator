package com.example.orchestrator.model;

/**
 * Status of a task in the execution plan.
 */
public enum TaskStatus {
    /**
     * Task is waiting for dependencies.
     */
    PENDING,

    /**
     * Task is ready to execute (dependencies satisfied).
     */
    READY,

    /**
     * Task is currently executing.
     */
    RUNNING,

    /**
     * Task completed successfully.
     */
    COMPLETED,

    /**
     * Task failed.
     */
    FAILED,

    /**
     * Task was skipped.
     */
    SKIPPED
}