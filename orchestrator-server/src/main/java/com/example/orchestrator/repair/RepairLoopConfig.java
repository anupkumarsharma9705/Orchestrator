package com.example.orchestrator.repair;

public record RepairLoopConfig(
    int maxAttempts,
    int attemptTimeoutMinutes,
    int totalTimeoutMinutes
) {
    public static RepairLoopConfig defaultConfig() {
        return new RepairLoopConfig(3, 10, 30);
    }

    public static RepairLoopConfig aggressiveConfig() {
        return new RepairLoopConfig(5, 15, 60);
    }

    public static RepairLoopConfig conservativeConfig() {
        return new RepairLoopConfig(2, 5, 15);
    }

    public int getMaxAttempts() { return maxAttempts; }
    public int getAttemptTimeoutMinutes() { return attemptTimeoutMinutes; }
    public int getTotalTimeoutMinutes() { return totalTimeoutMinutes; }
}