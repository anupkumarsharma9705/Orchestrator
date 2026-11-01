package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.verify.Severity;
import com.example.orchestrator.verify.VerificationResult;
import com.example.orchestrator.sandbox.SandboxService;
import com.example.orchestrator.sandbox.SandboxResult;
import com.example.orchestrator.verify.VerifyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Runner that executes all scanners in parallel using the sandbox.
 */
@Component
public class VerificationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificationRunner.class);

    private final SandboxService sandboxService;
    private final ScannerRegistry scannerRegistry;
    private final VerifyConfig config;

    public VerificationRunner(SandboxService sandboxService, ScannerRegistry scannerRegistry, VerifyConfig config) {
        this.sandboxService = sandboxService;
        this.scannerRegistry = scannerRegistry;
        this.config = config;
    }

    /**
     * Runs all enabled scanners in parallel and returns aggregated results.
     */
    public VerificationResult run(Path workspacePath) {
        Instant start = Instant.now();
        
        List<String> enabledScanners = config.getEnabledScanners();
        log.info("Running {} scanners in parallel on workspace: {}", enabledScanners.size(), workspacePath);

        // Run scanners in parallel
        try (ExecutorService executor = Executors.newFixedThreadPool(config.getMaxParallelScanners())) {
            List<CompletableFuture<ScannerResult>> futures = enabledScanners.stream()
                .map(scannerName -> scannerRegistry.getScanner(scannerName))
                .filter(Optional::isPresent)
                .map(opt -> CompletableFuture.supplyAsync(() -> runScanner(opt.get(), workspacePath), executor))
                .collect(Collectors.toList());

            // Wait for all to complete
            List<ScannerResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            // Aggregate results
            return aggregateResults(results, Duration.between(start, Instant.now()));
        }
    }

    private ScannerResult runScanner(Scanner scanner, Path workspacePath) {
        String scannerName = scanner.name();
        log.info("Starting scanner: {}", scannerName);
        
        Instant start = Instant.now();
        SandboxResult result = sandboxService.run(
            scanner.command(workspacePath), 
            workspacePath, 
            config.getDefaultTimeoutSeconds()
        );
        Duration duration = Duration.between(start, Instant.now());

        if (result.timedOut()) {
            log.warn("Scanner {} timed out after {}", scannerName, duration);
            return new ScannerResult(scannerName, false, List.of(), result.stdout(), result.stderr(), duration);
        }

        if (result.exitCode() != 0 && !result.oomKilled()) {
            log.warn("Scanner {} exited with code {}", scannerName, result.exitCode());
        }

        List<Finding> findings = scanner.parseOutput(workspacePath, result.stdout(), result.stderr());
        
        // Apply severity mapping
        findings = findings.stream()
            .map(f -> mapSeverity(f, scannerName))
            .collect(Collectors.toList());

        log.info("Scanner {} completed in {} with {} findings", scannerName, duration, findings.size());
        
        return new ScannerResult(scannerName, true, findings, result.stdout(), result.stderr(), duration);
    }

    private Finding mapSeverity(Finding finding, String scannerName) {
        Map<String, String> mapping = config.getSeverityMapping().get(scannerName);
        if (mapping != null && mapping.containsKey(finding.severity().name())) {
            String mapped = mapping.get(finding.severity().name());
            try {
                Severity mappedSeverity = Severity.valueOf(mapped);
                return new Finding(
                    finding.id(), finding.tool(), finding.ruleId(), 
                    mappedSeverity, finding.message(), finding.filePath(),
                    finding.lineNumber(), finding.codeSnippet(), 
                    finding.fingerprint(), finding.metadata()
                );
            } catch (IllegalArgumentException e) {
                log.warn("Invalid severity mapping for {}: {}", scannerName, mapped);
            }
        }
        return finding;
    }

    private VerificationResult aggregateResults(List<ScannerResult> results, Duration totalDuration) {
        boolean buildOk = results.stream().allMatch(r -> r.success() && r.exitCode() == 0);
        boolean testsPassed = results.stream()
            .filter(r -> "test".equals(r.scannerName()))
            .allMatch(r -> r.success() && r.exitCode() == 0);
        
        List<Finding> allFindings = results.stream()
            .flatMap(r -> r.findings().stream())
            .collect(Collectors.toList());
        
        // Deduplicate by fingerprint
        List<Finding> uniqueFindings = allFindings.stream()
            .collect(Collectors.toMap(
                Finding::fingerprint, 
                f -> f, 
                (f1, f2) -> f1
            ))
            .values()
            .stream()
            .collect(Collectors.toList());

        double coveragePercent = results.stream()
            .filter(r -> "coverage".equals(r.scannerName()))
            .mapToDouble(r -> extractCoverage(r.stdout()))
            .max()
            .orElse(0.0);

        String combinedOutput = results.stream()
            .map(ScannerResult::combinedOutput)
            .collect(Collectors.joining("\n\n=== SCANNER: "));

        return new VerificationResult(buildOk, testsPassed, coveragePercent, uniqueFindings, totalDuration, combinedOutput);
    }

    private double extractCoverage(String output) {
        // Extract coverage percentage from various tool outputs
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)%\\s*(coverage|covered)");
        java.util.regex.Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}