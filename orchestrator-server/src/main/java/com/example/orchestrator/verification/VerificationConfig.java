package com.example.orchestrator.verification;

public record VerificationConfig(
    boolean compileEnabled,
    boolean unitTestEnabled,
    boolean semgrepEnabled,
    boolean dependencyCheckEnabled,
    boolean trivyEnabled,
    boolean secretScanEnabled,
    boolean dockerBuildEnabled,
    boolean dockerImageScanEnabled
) {
    public static VerificationConfig defaultConfig() {
        return new VerificationConfig(true, true, false, false, false, false, false, false);
    }

    public static VerificationConfig fullConfig() {
        return new VerificationConfig(true, true, true, true, true, true, true, true);
    }

    public boolean isCompileEnabled() { return compileEnabled; }
    public boolean isUnitTestEnabled() { return unitTestEnabled; }
    public boolean isSemgrepEnabled() { return semgrepEnabled; }
    public boolean isDependencyCheckEnabled() { return dependencyCheckEnabled; }
    public boolean isTrivyEnabled() { return trivyEnabled; }
    public boolean isSecretScanEnabled() { return secretScanEnabled; }
    public boolean isDockerBuildEnabled() { return dockerBuildEnabled; }
    public boolean isDockerImageScanEnabled() { return dockerImageScanEnabled; }
}