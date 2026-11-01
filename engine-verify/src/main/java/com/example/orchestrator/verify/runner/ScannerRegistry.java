package com.example.orchestrator.verify.runner;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of available scanners.
 */
@Component
public class ScannerRegistry {

    private final Map<String, Scanner> scanners = new HashMap<>();

    private final CompileScanner compileScanner;
    private final TestScanner testScanner;
    private final CoverageScanner coverageScanner;
    private final SpotBugsScanner spotBugsScanner;
    private final CheckstyleScanner checkstyleScanner;
    private final SemgrepScanner semgrepScanner;
    private final DepCheckScanner depCheckScanner;
    private final TrivyScanner trivyScanner;
    private final SecretsScanner secretsScanner;

    public ScannerRegistry(
        CompileScanner compileScanner,
        TestScanner testScanner,
        CoverageScanner coverageScanner,
        SpotBugsScanner spotBugsScanner,
        CheckstyleScanner checkstyleScanner,
        SemgrepScanner semgrepScanner,
        DepCheckScanner depCheckScanner,
        TrivyScanner trivyScanner,
        SecretsScanner secretsScanner
    ) {
        this.compileScanner = compileScanner;
        this.testScanner = testScanner;
        this.coverageScanner = coverageScanner;
        this.spotBugsScanner = spotBugsScanner;
        this.checkstyleScanner = checkstyleScanner;
        this.semgrepScanner = semgrepScanner;
        this.depCheckScanner = depCheckScanner;
        this.trivyScanner = trivyScanner;
        this.secretsScanner = secretsScanner;
    }

    @PostConstruct
    public void init() {
        register(compileScanner);
        register(testScanner);
        register(coverageScanner);
        register(spotBugsScanner);
        register(checkstyleScanner);
        register(semgrepScanner);
        register(depCheckScanner);
        register(trivyScanner);
        register(secretsScanner);
    }

    private void register(Scanner scanner) {
        scanners.put(scanner.name(), scanner);
    }

    public Optional<Scanner> getScanner(String name) {
        return Optional.ofNullable(scanners.get(name));
    }

    public List<Scanner> getAllScanners() {
        return List.copyOf(scanners.values());
    }
}