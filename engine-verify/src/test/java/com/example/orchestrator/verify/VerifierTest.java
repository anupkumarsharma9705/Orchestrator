package com.example.orchestrator.verify;

import com.example.orchestrator.verify.runner.CoverageScanner;
import com.example.orchestrator.verify.runner.ScannerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Verifier module.
 */
@SpringBootTest(classes = {com.example.orchestrator.verify.VerifierTestConfig.class})
@ActiveProfiles("test")
class VerifierTest {

    @Autowired
    private ScannerRegistry scannerRegistry;

    @Test
    void scannerRegistry_containsAllScanners() {
        assertThat(scannerRegistry.getAllScanners()).hasSize(9);
        
        assertThat(scannerRegistry.getScanner("compile")).isPresent();
        assertThat(scannerRegistry.getScanner("test")).isPresent();
        assertThat(scannerRegistry.getScanner("coverage")).isPresent();
        assertThat(scannerRegistry.getScanner("spotbugs")).isPresent();
        assertThat(scannerRegistry.getScanner("checkstyle")).isPresent();
        assertThat(scannerRegistry.getScanner("semgrep")).isPresent();
        assertThat(scannerRegistry.getScanner("depcheck")).isPresent();
        assertThat(scannerRegistry.getScanner("trivy")).isPresent();
        assertThat(scannerRegistry.getScanner("secrets")).isPresent();
    }

    @Test
    void compileScanner_parsesErrors() {
        var scanner = scannerRegistry.getScanner("compile").get();
        
        String output = "[ERROR] /path/to/File.java:[10,5] Cannot find symbol";
        List<Finding> findings = scanner.parseOutput(Paths.get("/workspace"), output, "");
        
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(findings.get(0).ruleId()).isEqualTo("COMPILE_ERROR");
    }

    @Test
    void testScanner_parsesFailures() {
        var scanner = scannerRegistry.getScanner("test").get();
        
        String output = "Tests run: 10, Failures: 2, Errors: 1, Skipped: 0\n<<< FAILURE! <<< \nTest failed\n  at com.example.Test.method(Test.java:10)";
        List<Finding> findings = scanner.parseOutput(Paths.get("/workspace"), output, "");
        
        assertThat(findings).hasSizeGreaterThanOrEqualTo(1);
        assertThat(findings.stream().anyMatch(f -> f.severity() == Severity.HIGH)).isTrue();
    }

    @Test
    void coverageScanner_detectsLowCoverage() {
        var scanner = scannerRegistry.getScanner("coverage").get();
        
        // Scanner looks for "Line XX%" pattern
        String output = "Line       75%  100  25";
        List<Finding> findings = scanner.parseOutput(Paths.get("/workspace"), output, "");
        
        // Should detect coverage below 80%
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(findings.get(0).ruleId()).isEqualTo("COVERAGE_BELOW_THRESHOLD");
    }

    @Test
    void finding_fingerprint_isConsistent() {
        Finding f1 = new Finding("1", "tool", "RULE", Severity.HIGH, "msg", "file.java", 10, "code", "", java.util.Map.of());
        Finding f2 = new Finding("2", "tool", "RULE", Severity.HIGH, "msg", "file.java", 10, "code", "", java.util.Map.of());
        
        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
    }

    @Test
    void finding_fingerprint_isUnique() {
        Finding f1 = new Finding("1", "tool", "RULE", Severity.HIGH, "msg", "file.java", 10, "code", "", java.util.Map.of());
        Finding f2 = new Finding("2", "tool", "RULE", Severity.HIGH, "msg", "file.java", 11, "code", "", java.util.Map.of());
        
        assertThat(f1).isNotEqualTo(f2);
    }

    @Test
    void severity_order() {
        assertThat(Severity.CRITICAL.ordinal()).isLessThan(Severity.HIGH.ordinal());
        assertThat(Severity.HIGH.ordinal()).isLessThan(Severity.MEDIUM.ordinal());
        assertThat(Severity.MEDIUM.ordinal()).isLessThan(Severity.LOW.ordinal());
        assertThat(Severity.LOW.ordinal()).isLessThan(Severity.INFO.ordinal());
    }
}