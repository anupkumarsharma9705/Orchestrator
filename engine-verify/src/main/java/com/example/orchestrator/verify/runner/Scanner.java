package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.sandbox.SandboxResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for a verification scanner.
 */
public interface Scanner {
    /**
     * Returns the unique name of this scanner.
     */
    String name();

    /**
     * Returns the command to run this scanner in the sandbox.
     */
    String command(Path workspacePath);

    /**
     * Parses the scanner output into a list of findings.
     */
    List<Finding> parseOutput(Path workspacePath, String stdout, String stderr);
}

/**
 * Result of running a single scanner.
 */
record ScannerResult(
    String scannerName,
    boolean success,
    List<com.example.orchestrator.verify.Finding> findings,
    String stdout,
    String stderr,
    java.time.Duration duration
) {
    int exitCode() {
        return success ? 0 : -1;
    }
    
    String combinedOutput() {
        return "SCANNER: " + scannerName + "\nSTDOUT:\n" + stdout + "\nSTDERR:\n" + stderr;
    }
}