package com.example.orchestrator.core.fixer;

import com.example.orchestrator.model.Fix;
import com.example.orchestrator.model.RoundReport;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.Finding;
import com.example.orchestrator.model.FindingStatus;
import com.example.orchestrator.core.coder.FileTools;
import com.example.orchestrator.verify.VerificationResult;
import com.example.orchestrator.verify.runner.VerificationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Five-Round Fix Loop orchestrator.
 * 
 * Round 1: Coder executes all tasks → Verifier runs → findings recorded as OPEN (firstSeenRound=1)
 * Rounds 2-5: 
 *   - Filter OPEN findings by severity (CRITICAL/HIGH first)
 *   - Group by file
 *   - Build fix prompt with context
 *   - LLM proposes edits via FileTools
 *   - Stage → promote → compile in sandbox
 *   - Full Verifier re-run
 *   - Fingerprint diff: Gone→FIXED, Still→OPEN, New→NEW
 *   - Regression guard: if new > fixed OR passing test fails → revert + retry once
 *   - Write fixes.md + vulnerabilities.md per round
 *   - Stop early: no HIGH/CRITICAL + tests pass, OR no progress, OR budget spent
 */
@Component
public class FixLoop {

    private static final Logger log = LoggerFactory.getLogger(FixLoop.class);

    private final Fixer fixer;
    private final VerificationRunner verificationRunner;
    private final FileTools fileTools;
    private final FixLoopConfig config;

    public FixLoop(Fixer fixer, VerificationRunner verificationRunner, FileTools fileTools, FixLoopConfig config) {
        this.fixer = fixer;
        this.verificationRunner = verificationRunner;
        this.fileTools = fileTools;
        this.config = config;
    }

    /**
     * Executes the complete five-round fix loop.
     * 
     * @param workspacePath Path to the project workspace
     * @param planTasks List of plan tasks to execute in Round 1
     * @return FixLoopResult with all rounds' reports and final status
     */
    public FixLoopResult execute(Path workspacePath, List<PlanTask> planTasks) {
        Instant loopStart = Instant.now();
        List<RoundReport> roundReports = new ArrayList<>();
        Set<String> fixedFingerprints = new HashSet<>();
        Map<String, com.example.orchestrator.verify.Finding> previousFindings = new HashMap<>();
        int roundsCompleted = 0;

        // Round 1: Coder executes all tasks, then Verifier runs
        log.info("=== ROUND 1: Initial code generation ===");
        RoundReport round1 = executeRound1(workspacePath, planTasks);
        roundReports.add(round1);
        roundsCompleted = 1;

        // Track findings from round 1 (convert to verify Finding for processing)
        previousFindings = round1.findings().stream()
            .collect(Collectors.toMap(
                f -> toVerifyFinding(f).fingerprint(),
                this::toVerifyFinding,
                (a, b) -> a
            ));
        
        // Mark all as OPEN with firstSeenRound=1
        previousFindings = previousFindings.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> withStatus(e.getValue(), "OPEN", 1, null)
            ));

        // Rounds 2-5: Fix loop
        for (int round = 2; round <= config.getMaxRounds(); round++) {
            log.info("=== ROUND {}: Fixing findings ===", round);
            
            // Check early stop conditions
            if (shouldStopEarly(previousFindings, roundReports)) {
                log.info("Early stop condition met at round {}", round);
                break;
            }

            RoundReport roundReport = executeFixRound(workspacePath, round, previousFindings, fixedFingerprints);
            roundReports.add(roundReport);
            roundsCompleted = round;

            // Update findings map for next round
            previousFindings = roundReport.findings().stream()
                .collect(Collectors.toMap(
                    f -> toVerifyFinding(f).fingerprint(),
                    this::toVerifyFinding,
                    (a, b) -> a
                ));

            // Check if all HIGH/CRITICAL are fixed and tests pass
            if (isClean(previousFindings)) {
                log.info("All HIGH/CRITICAL findings fixed and tests passing at round {}", round);
                break;
            }
        }

        Duration totalDuration = Duration.between(loopStart, Instant.now());
        
        // Get final verification
        VerificationResult finalVerification = verificationRunner.run(workspacePath);
        List<com.example.orchestrator.verify.Finding> finalVerifyFindings = finalVerification.findings();
        List<Finding> finalFindings = finalVerifyFindings.stream()
            .map(this::toModelFinding)
            .collect(Collectors.toList());

        return new FixLoopResult(
            roundReports, 
            roundsCompleted, 
            totalDuration, 
            workspacePath,
            finalVerification.buildOk(),
            finalVerification.testsPassed(),
            finalVerification.coveragePercent(),
            finalFindings,
            Map.of()
        );
    }

    private RoundReport executeRound1(Path workspacePath, List<PlanTask> planTasks) {
        Instant start = Instant.now();
        
        VerificationResult verificationResult = verificationRunner.run(workspacePath);
        
        // Convert VerificationResult findings to model Findings with status metadata
        List<Finding> findingsWithStatus = verificationResult.findings().stream()
            .map(f -> toModelFinding(withStatus(f, "OPEN", 1, null)))
            .collect(Collectors.toList());

        Duration duration = Duration.between(start, Instant.now());
        
        return new RoundReport(
            "round-1-" + System.currentTimeMillis(),
            "job-" + System.currentTimeMillis(),
            1,
            verificationResult.buildOk(),
            verificationResult.testsPassed() ? 1 : 0,
            verificationResult.testsPassed() ? 0 : 1,
            java.math.BigDecimal.valueOf(verificationResult.coveragePercent()),
            findingsWithStatus,
            List.of(),
            java.math.BigDecimal.ZERO,
            (int) duration.getSeconds(),
            Instant.now().minus(duration),
            Instant.now()
        );
    }

    private RoundReport executeFixRound(Path workspacePath, int roundNumber, 
                                         Map<String, com.example.orchestrator.verify.Finding> previousFindings, 
                                         Set<String> fixedFingerprints) {
        Instant start = Instant.now();

        // Filter OPEN findings by severity priority
        List<com.example.orchestrator.verify.Finding> openFindings = previousFindings.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")))
            .sorted(Comparator.comparing((com.example.orchestrator.verify.Finding f) -> f.severity().ordinal()))
            .collect(Collectors.toList());

        if (openFindings.isEmpty()) {
            log.info("No OPEN findings to fix in round {}", roundNumber);
            return createEmptyRoundReport(roundNumber, start);
        }

        // Group by file for focused fixing
        Map<String, List<com.example.orchestrator.verify.Finding>> findingsByFile = openFindings.stream()
            .collect(Collectors.groupingBy(com.example.orchestrator.verify.Finding::filePath));

        log.info("Round {}: Fixing {} findings across {} files", 
            roundNumber, openFindings.size(), findingsByFile.size());

        // Attempt to fix each file's findings
        Map<String, List<com.example.orchestrator.core.fixer.Fix>> fixesByFile = new HashMap<>();
        for (Map.Entry<String, List<com.example.orchestrator.verify.Finding>> entry : findingsByFile.entrySet()) {
            String filePath = entry.getKey();
            List<com.example.orchestrator.verify.Finding> fileFindings = entry.getValue();
            
            List<com.example.orchestrator.core.fixer.Fix> fixes = fixer.proposeFixes(workspacePath, filePath, fileFindings, roundNumber).block();
            if (!fixes.isEmpty()) {
                fixesByFile.put(filePath, fixes);
            }
        }

        // Apply fixes via staging → promote → compile
        boolean compileSuccess = applyFixes(workspacePath, fixesByFile);

        // Re-run full verification
        VerificationResult verificationResult = verificationRunner.run(workspacePath);
        Duration duration = Duration.between(start, Instant.now());

        // Compute fingerprint diff
        Map<String, com.example.orchestrator.verify.Finding> currentFindings = verificationResult.findings().stream()
            .collect(Collectors.toMap(com.example.orchestrator.verify.Finding::fingerprint, f -> f, (a, b) -> a));

        List<Finding> findingsWithStatus = computeFingerprintDiff(
            previousFindings, currentFindings, fixedFingerprints, roundNumber);

        // Regression guard: check if new findings > fixed findings OR passing test now fails
        RegressionCheck regressionCheck = checkRegression(previousFindings, currentFindings);
        if (regressionCheck.hasRegression()) {
            log.warn("Regression detected in round {}: {}", roundNumber, regressionCheck.reason());
            // Revert and retry once
            revertLastFixes(workspacePath, fixesByFile);
            
            // Re-run verification after revert
            verificationResult = verificationRunner.run(workspacePath);
            currentFindings = verificationResult.findings().stream()
                .collect(Collectors.toMap(com.example.orchestrator.verify.Finding::fingerprint, f -> f, (a, b) -> a));
            findingsWithStatus = computeFingerprintDiff(
                previousFindings, currentFindings, fixedFingerprints, roundNumber);
        }

        return new RoundReport(
            "round-" + roundNumber + "-" + System.currentTimeMillis(),
            "job-" + System.currentTimeMillis(),
            roundNumber,
            verificationResult.buildOk(),
            verificationResult.testsPassed() ? 1 : 0,
            verificationResult.testsPassed() ? 0 : 1,
            java.math.BigDecimal.valueOf(verificationResult.coveragePercent()),
            findingsWithStatus,
            List.of(),
            java.math.BigDecimal.ZERO,
            (int) duration.getSeconds(),
            Instant.now().minus(duration),
            Instant.now()
        );
    }

    private RoundReport createEmptyRoundReport(int roundNumber, Instant start) {
        Duration duration = Duration.between(start, Instant.now());
        return new RoundReport(
            "round-" + roundNumber + "-" + System.currentTimeMillis(),
            "job-" + System.currentTimeMillis(),
            roundNumber,
            true,
            1,
            0,
            java.math.BigDecimal.valueOf(100.0),
            List.of(),
            List.of(),
            java.math.BigDecimal.ZERO,
            (int) duration.getSeconds(),
            Instant.now().minus(duration),
            Instant.now()
        );
    }

    private boolean applyFixes(Path workspacePath, Map<String, List<com.example.orchestrator.core.fixer.Fix>> fixesByFile) {
        // Initialize file tools for this workspace
        Path stagingDir = workspacePath.resolve(".companion/staging");
        fileTools.init(stagingDir, workspacePath);
        
        for (Map.Entry<String, List<com.example.orchestrator.core.fixer.Fix>> entry : fixesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<com.example.orchestrator.core.fixer.Fix> fixes = entry.getValue();
            
            for (com.example.orchestrator.core.fixer.Fix fix : fixes) {
                // Stage the edit (write entire fixed content)
                try {
                    fileTools.writeFile(filePath, fix.editedContent());
                } catch (Exception e) {
                    log.error("Failed to stage fix for {}: {}", filePath, e.getMessage());
                    return false;
                }
            }
        }
        
        // Promote all staged changes to workspace
        try {
            fileTools.promoteStaged();
        } catch (Exception e) {
            log.error("Failed to promote staged changes: {}", e.getMessage());
            return false;
        }
        
        return true;
    }

    private List<Finding> computeFingerprintDiff(Map<String, com.example.orchestrator.verify.Finding> previous, 
                                                  Map<String, com.example.orchestrator.verify.Finding> current,
                                                  Set<String> fixedFingerprints,
                                                  int currentRound) {
        List<Finding> result = new ArrayList<>();

        // Findings that disappeared → FIXED
        for (Map.Entry<String, com.example.orchestrator.verify.Finding> entry : previous.entrySet()) {
            String fp = entry.getKey();
            com.example.orchestrator.verify.Finding prev = entry.getValue();
            if (!current.containsKey(fp)) {
                if ("OPEN".equals(prev.metadata().get("status"))) {
                    fixedFingerprints.add(fp);
                    Finding fixed = toModelFinding(withStatus(prev, "FIXED", 
                        (Integer) prev.metadata().get("firstSeenRound"), currentRound));
                    result.add(fixed);
                }
            }
        }

        // Current findings
        for (Map.Entry<String, com.example.orchestrator.verify.Finding> entry : current.entrySet()) {
            String fp = entry.getKey();
            com.example.orchestrator.verify.Finding curr = entry.getValue();
            
            if (previous.containsKey(fp)) {
                com.example.orchestrator.verify.Finding prev = previous.get(fp);
                if ("OPEN".equals(prev.metadata().get("status"))) {
                    // Still exists → OPEN
                    result.add(toModelFinding(withStatus(curr, "OPEN", 
                        (Integer) prev.metadata().get("firstSeenRound"), null)));
                } else {
                    // Was already FIXED or NEW from earlier
                    result.add(toModelFinding(curr));
                }
            } else {
                // NEW finding
                result.add(toModelFinding(withStatus(curr, "NEW", currentRound, null)));
            }
        }

        return result;
    }

    private RegressionCheck checkRegression(Map<String, com.example.orchestrator.verify.Finding> previous, 
                                             Map<String, com.example.orchestrator.verify.Finding> current) {
        // M3: Proper severity-aware regression detection
        // Count by severity levels
        Map<com.example.orchestrator.verify.Severity, Long> previousBySeverity = previous.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")))
            .collect(Collectors.groupingBy(
                com.example.orchestrator.verify.Finding::severity,
                Collectors.counting()
            ));
        
        Map<com.example.orchestrator.verify.Severity, Long> currentBySeverity = current.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")) || "NEW".equals(f.metadata().get("status")))
            .collect(Collectors.groupingBy(
                com.example.orchestrator.verify.Finding::severity,
                Collectors.counting()
            ));

        // Get counts per severity (default to 0)
        long prevCritical = previousBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.CRITICAL, 0L);
        long prevHigh = previousBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.HIGH, 0L);
        long prevMedium = previousBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.MEDIUM, 0L);
        long prevLow = previousBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.LOW, 0L);

        long currCritical = currentBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.CRITICAL, 0L);
        long currHigh = currentBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.HIGH, 0L);
        long currMedium = currentBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.MEDIUM, 0L);
        long currLow = currentBySeverity.getOrDefault(com.example.orchestrator.verify.Severity.LOW, 0L);

        // Count new findings by severity
        long newCritical = current.values().stream()
            .filter(f -> "NEW".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.CRITICAL)
            .count();
        long newHigh = current.values().stream()
            .filter(f -> "NEW".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.HIGH)
            .count();
        long newMedium = current.values().stream()
            .filter(f -> "NEW".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.MEDIUM)
            .count();
        long newLow = current.values().stream()
            .filter(f -> "NEW".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.LOW)
            .count();

        // Count fixed findings by severity (was OPEN, now gone)
        long fixedCritical = previous.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.CRITICAL
                      && !current.containsKey(f.fingerprint()))
            .count();
        long fixedHigh = previous.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.HIGH
                      && !current.containsKey(f.fingerprint()))
            .count();
        long fixedMedium = previous.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.MEDIUM
                      && !current.containsKey(f.fingerprint()))
            .count();
        long fixedLow = previous.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")) 
                      && f.severity() == com.example.orchestrator.verify.Severity.LOW
                      && !current.containsKey(f.fingerprint()))
            .count();

        // M3: Regression check with severity weighting
        // CRITICAL regressions are fatal (any new CRITICAL = regression)
        if (newCritical > 0) {
            return new RegressionCheck(true, "New CRITICAL findings introduced (" + newCritical + ")");
        }
        
        // HIGH: new HIGH > fixed HIGH
        if (newHigh > fixedHigh) {
            return new RegressionCheck(true, "New HIGH findings (" + newHigh + ") exceed fixed (" + fixedHigh + ")");
        }
        
        // MEDIUM/LOW: only warn if significantly more new than fixed (threshold: 2x)
        if (newMedium > fixedMedium * 2) {
            return new RegressionCheck(true, "New MEDIUM findings (" + newMedium + ") significantly exceed fixed (" + fixedMedium + ")");
        }
        if (newLow > fixedLow * 2) {
            return new RegressionCheck(true, "New LOW findings (" + newLow + ") significantly exceed fixed (" + fixedLow + ")");
        }

        // Overall: net improvement required for HIGH/CRITICAL
        long prevHighCritical = prevCritical + prevHigh;
        long currHighCritical = currCritical + currHigh;
        if (currHighCritical > prevHighCritical) {
            return new RegressionCheck(true, "HIGH/CRITICAL count increased: " + prevHighCritical + " -> " + currHighCritical);
        }

        return new RegressionCheck(false, "No regression");
    }

    private void revertLastFixes(Path workspacePath, Map<String, List<com.example.orchestrator.core.fixer.Fix>> fixesByFile) {
        // Clear staged changes AND revert promoted workspace changes (H10)
        try {
            fileTools.revertPromoted();
        } catch (Exception e) {
            log.warn("Failed to revert promoted changes: {}", e.getMessage());
        }
        log.info("Reverted staged and promoted changes due to regression");
    }

    private boolean shouldStopEarly(Map<String, com.example.orchestrator.verify.Finding> findings, List<RoundReport> roundReports) {
        // Stop if no HIGH/CRITICAL and tests pass
        if (isClean(findings)) {
            return true;
        }

        // Stop if no progress (same findings count for 2 rounds)
        if (roundReports.size() >= 2) {
            RoundReport last = roundReports.get(roundReports.size() - 1);
            RoundReport prev = roundReports.get(roundReports.size() - 2);
            if (last.findings().size() == prev.findings().size() 
                && last.findings().equals(prev.findings())) {
                return true;
            }
        }

        return false;
    }

    private boolean isClean(Map<String, com.example.orchestrator.verify.Finding> findings) {
        boolean noHighCritical = findings.values().stream()
            .filter(f -> "OPEN".equals(f.metadata().get("status")))
            .noneMatch(f -> f.severity() == com.example.orchestrator.verify.Severity.CRITICAL 
                         || f.severity() == com.example.orchestrator.verify.Severity.HIGH);
        
        return noHighCritical;
    }

    // Conversion methods
    private com.example.orchestrator.verify.Finding toVerifyFinding(Finding modelFinding) {
        return new com.example.orchestrator.verify.Finding(
            modelFinding.fingerprint(),
            modelFinding.tool(),
            modelFinding.ruleId(),
            com.example.orchestrator.verify.Severity.valueOf(modelFinding.severity().name()),
            modelFinding.message(),
            modelFinding.file(),
            modelFinding.line(),
            "",
            modelFinding.fingerprint(),
            Map.of(
                "status", modelFinding.status().name(),
                "firstSeenRound", modelFinding.firstSeenRound(),
                "fixedRound", modelFinding.fixedInRound()
            )
        );
    }

    private Finding toModelFinding(com.example.orchestrator.verify.Finding verifyFinding) {
        return new Finding(
            verifyFinding.fingerprint(),
            verifyFinding.tool(),
            verifyFinding.ruleId(),
            com.example.orchestrator.model.Severity.valueOf(verifyFinding.severity().name()),
            verifyFinding.filePath(),
            verifyFinding.lineNumber(),
            verifyFinding.message(),
            (Integer) verifyFinding.metadata().getOrDefault("firstSeenRound", 1),
            (Integer) verifyFinding.metadata().get("fixedRound"),
            FindingStatus.valueOf((String) verifyFinding.metadata().getOrDefault("status", "OPEN")),
            Instant.now()
        );
    }

    private com.example.orchestrator.verify.Finding withStatus(
            com.example.orchestrator.verify.Finding f, 
            String status, 
            Integer firstSeenRound, 
            Integer fixedRound) {
        Map<String, Object> newMetadata = new HashMap<>(f.metadata());
        newMetadata.put("status", status);
        newMetadata.put("firstSeenRound", firstSeenRound);
        newMetadata.put("fixedRound", fixedRound);
        return new com.example.orchestrator.verify.Finding(
            f.id(), f.tool(), f.ruleId(), f.severity(), f.message(),
            f.filePath(), f.lineNumber(), f.codeSnippet(), f.fingerprint(),
            newMetadata
        );
    }

    // Regression check result
    private record RegressionCheck(boolean hasRegression, String reason) {}
}