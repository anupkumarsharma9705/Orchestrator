package com.example.orchestrator.core.hardening;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Logger wrapper that automatically adds job-id correlation to all log messages.
 * Uses SLF4J MDC (Mapped Diagnostic Context) for thread-local correlation IDs.
 */
@Component
public class CorrelationLogger {

    public static final String JOB_ID_KEY = "jobId";
    public static final String ROUND_KEY = "round";
    public static final String TASK_KEY = "taskId";
    public static final String SPEC_KEY = "specId";

    private final Logger logger;

    public CorrelationLogger() {
        this.logger = LoggerFactory.getLogger(CorrelationLogger.class);
    }

    public CorrelationLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Creates a new correlation context with a generated job ID.
     * 
     * @return The generated job ID
     */
    public String startJob() {
        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put(JOB_ID_KEY, jobId);
        MDC.remove(ROUND_KEY);
        MDC.remove(TASK_KEY);
        MDC.remove(SPEC_KEY);
        return jobId;
    }

    /**
     * Starts a job with a specific job ID.
     */
    public void startJob(String jobId) {
        MDC.put(JOB_ID_KEY, jobId);
        MDC.remove(ROUND_KEY);
        MDC.remove(TASK_KEY);
        MDC.remove(SPEC_KEY);
    }

    /**
     * Sets the current round number.
     */
    public void setRound(int round) {
        MDC.put(ROUND_KEY, String.valueOf(round));
    }

    /**
     * Sets the current task ID.
     */
    public void setTask(String taskId) {
        MDC.put(TASK_KEY, taskId);
    }

    /**
     * Sets the current spec ID (for evaluation).
     */
    public void setSpec(String specId) {
        MDC.put(SPEC_KEY, specId);
    }

    /**
     * Clears all correlation context.
     */
    public void clear() {
        MDC.clear();
    }

    /**
     * Gets the current job ID.
     */
    public String getJobId() {
        return MDC.get(JOB_ID_KEY);
    }

    /**
     * Gets the current round.
     */
    public String getRound() {
        return MDC.get(ROUND_KEY);
    }

    /**
     * Executes a function with a new job correlation context.
     */
    public <T> T withJob(Supplier<T> supplier) {
        String jobId = startJob();
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * Executes a function with a specific job ID.
     */
    public <T> T withJob(String jobId, Supplier<T> supplier) {
        startJob(jobId);
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * Executes a function with job and round context.
     */
    public <T> T withRound(String jobId, int round, Supplier<T> supplier) {
        startJob(jobId);
        setRound(round);
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }

    // Delegate logging methods that automatically include MDC context
    public void info(String message) {
        logger.info(message);
    }

    public void info(String format, Object... args) {
        logger.info(format, args);
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void debug(String format, Object... args) {
        logger.debug(format, args);
    }

    public void warn(String message) {
        logger.warn(message);
    }

    public void warn(String format, Object... args) {
        logger.warn(format, args);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void error(String format, Object... args) {
        logger.error(format, args);
    }

    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    public void error(String format, Throwable throwable, Object... args) {
        logger.error(format, throwable, args);
    }
}