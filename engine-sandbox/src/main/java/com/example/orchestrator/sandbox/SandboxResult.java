package com.example.orchestrator.sandbox;

import java.time.Duration;

/**
 * Result of a sandbox execution.
 */
public record SandboxResult(
    int exitCode,
    String stdout,
    String stderr,
    Duration duration,
    boolean timedOut,
    boolean oomKilled
) {
    public static SandboxResult success(int exitCode, String stdout, String stderr, Duration duration) {
        return new SandboxResult(exitCode, stdout, stderr, duration, false, false);
    }

    public static SandboxResult timeout(Duration duration) {
        return new SandboxResult(-1, "", "Execution timed out", duration, true, false);
    }

    public static SandboxResult oomKilled(int exitCode, String stdout, String stderr, Duration duration) {
        return new SandboxResult(exitCode, stdout, stderr, duration, false, true);
    }

    public static SandboxResult error(String message, Duration duration) {
        return new SandboxResult(-1, "", message, duration, false, false);
    }
}