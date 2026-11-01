package com.example.orchestrator.verify;

import com.example.orchestrator.sandbox.SandboxConfig;
import com.example.orchestrator.sandbox.SandboxResult;
import com.example.orchestrator.sandbox.SandboxService;
import com.example.orchestrator.verify.runner.CompileScanner;
import com.example.orchestrator.verify.runner.CoverageScanner;
import com.example.orchestrator.verify.runner.DepCheckScanner;
import com.example.orchestrator.verify.runner.ScannerRegistry;
import com.example.orchestrator.verify.runner.SecretsScanner;
import com.example.orchestrator.verify.runner.SemgrepScanner;
import com.example.orchestrator.verify.runner.SpotBugsScanner;
import com.example.orchestrator.verify.runner.TestScanner;
import com.example.orchestrator.verify.runner.TrivyScanner;
import com.example.orchestrator.verify.VerifyConfig;
import com.example.orchestrator.verify.runner.CheckstyleScanner;
import com.example.orchestrator.verify.runner.VerificationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * Test configuration for Verifier tests.
 * Uses mocked SandboxService to avoid Docker dependency.
 */
@Configuration
@Profile("test")
public class VerifierTestConfig {

    @Bean
    @Primary
    public SandboxConfig testSandboxConfig() {
        SandboxConfig config = new SandboxConfig();
        config.setDockerImage("orchestrator/sandbox:1.0.0");
        config.setDockerHost("unix:///var/run/docker.sock");
        config.setAllowedRegistries(List.of("docker.io", "ghcr.io"));
        config.setDeniedRegistries(List.of("malicious.registry.com"));
        config.setRequireExplicitTag(true);
        config.setAllowedTags(List.of("1.0.0", "1.0.1", "stable"));
        config.setDeniedImagePatterns(List.of("*:latest", "*:dev-*", "malicious/*"));
        config.setAllowedDockerHosts(List.of("unix:///var/run/docker.sock", "unix:///run/docker.sock", "npipe:////./pipe/docker_engine"));
        config.setRequireTlsForTcp(true);
        config.setAllowedCapabilities(List.of());
        config.setDeniedCapabilities(List.of(
            "CAP_SYS_ADMIN", "CAP_SYS_RESOURCE", "CAP_SYS_MODULE",
            "CAP_SYS_RAWIO", "CAP_SYS_PACCT", "CAP_SYS_NICE",
            "CAP_SYSLOG", "CAP_DAC_READ_SEARCH", "CAP_LINUX_IMMUTABLE",
            "CAP_MAC_ADMIN", "CAP_MAC_OVERRIDE", "CAP_BPF",
            "CAP_PERFMON", "CAP_CHECKPOINT_RESTORE"
        ));
        config.setSecurityOpts(List.of("no-new-privileges:true"));
        config.setApparmorProfile("docker-default");
        config.setSeccompProfile("default");
        config.setDefaultTimeoutSeconds(300);
        config.setMaxTimeoutSeconds(3600);
        config.setMemoryLimitMb(2048);
        config.setCpuLimitCores(2);
        config.setMaxProcesses(100);
        config.setMavenCacheVolume("orchestrator-maven-cache");
        config.setWorkspaceMountPath("/workspace");
        config.setEnableNetwork(false);
        return config;
    }

    @Bean
    @Primary
    public SandboxService sandboxService(SandboxConfig testSandboxConfig) {
        // Return a mock implementation that doesn't require Docker
        return new SandboxService(testSandboxConfig) {
            @Override
            @PostConstruct
            public void init() {
                // No-op: override init to avoid Docker client initialization
            }

            @Override
            public SandboxResult run(String command, java.nio.file.Path workspacePath, int timeoutSeconds) {
                if (command.contains("compile")) {
                    return SandboxResult.success(0, "[ERROR] /workspace/File.java:[10,5] Cannot find symbol", "", java.time.Duration.ofSeconds(1));
                } else if (command.contains("test")) {
                    return SandboxResult.success(0, "Tests run: 10, Failures: 2, Errors: 1, Skipped: 0\n<<< FAILURE! <<< \nTest failed\n  at com.example.Test.method(Test.java:10)", "", java.time.Duration.ofSeconds(5));
                } else if (command.contains("jacoco:report")) {
                    return SandboxResult.success(0, "Total      75%  100  25\nLine       85%  200  30", "", java.time.Duration.ofSeconds(2));
                } else if (command.contains("spotbugs")) {
                    return SandboxResult.success(0, "<BugCollection version=\"1.0\"><BugInstance instanceHash=\"abc123\" type=\"NP_NULL_ON_SOME_PATH\" priority=\"2\" category=\"CORRECTNESS\" patternId=\"NP_NULL_ON_SOME_PATH\" primaryLine=\"10\" class=\"com.example.Test\"><Message>Null pointer dereference</Message></BugInstance></BugCollection>", "", java.time.Duration.ofSeconds(3));
                } else if (command.contains("checkstyle")) {
                    return SandboxResult.success(0, "<checkstyle><file name=\"File.java\"><error line=\"10\" column=\"5\" severity=\"error\" source=\"com.puppycrawl.tools.checkstyle.checks.naming.TypeNameCheck\" message=\"Type name must start with uppercase\"/></file></checkstyle>", "", java.time.Duration.ofSeconds(2));
                } else if (command.contains("semgrep")) {
                    return SandboxResult.success(0, "{\"results\": [{\"check_id\": \"security.injection\", \"path\": \"File.java\", \"start\": {\"line\": 10}, \"extra\": {\"message\": \"Potential SQL injection\", \"severity\": \"ERROR\"}}]}", "", java.time.Duration.ofSeconds(2));
                } else if (command.contains("dependency-check")) {
                    return SandboxResult.success(0, "<Report><Dependency filePath=\"lib.jar\" fileName=\"lib.jar\"><Vulnerability name=\"CVE-2023-1234\" severity=\"HIGH\" description=\"SQL injection vulnerability\" cwe=\"CWE-89\" cvssScore=\"9.8\"/></Dependency></Report>", "", java.time.Duration.ofSeconds(3));
                } else if (command.contains("trivy")) {
                    return SandboxResult.success(0, "{\"Results\": [{\"Vulnerabilities\": [{\"VulnerabilityID\": \"CVE-2023-1234\", \"PkgName\": \"lib\", \"InstalledVersion\": \"1.0\", \"FixedVersion\": \"2.0\", \"Severity\": \"HIGH\", \"Description\": \"Buffer overflow\"}]}]}", "", java.time.Duration.ofSeconds(3));
                } else if (command.contains("secrets")) {
                    return SandboxResult.success(0, "{\"results\": [{\"check_id\": \"generic.api-key\", \"path\": \"config.java\", \"start\": {\"line\": 5}, \"extra\": {\"message\": \"API key found\", \"severity\": \"ERROR\"}}]}", "", java.time.Duration.ofSeconds(2));
                }
                return SandboxResult.success(0, "", "", java.time.Duration.ofSeconds(1));
            }
        };
    }

    @Bean
    public VerifyConfig verifyConfig() {
        VerifyConfig config = new VerifyConfig();
        config.setSandboxImage("orchestrator/sandbox:latest");
        config.setDefaultTimeoutSeconds(300);
        config.setMaxParallelScanners(4);
        config.setEnabledScanners(List.of("compile", "test", "coverage", "spotbugs", "checkstyle", "semgrep", "depcheck", "trivy", "secrets"));
        config.setSeverityMapping(Map.of(
            "spotbugs", Map.of("HIGH", "HIGH", "MEDIUM", "MEDIUM", "LOW", "LOW"),
            "checkstyle", Map.of("error", "HIGH", "warning", "MEDIUM", "info", "LOW"),
            "semgrep", Map.of("ERROR", "HIGH", "WARNING", "MEDIUM", "INFO", "LOW"),
            "trivy", Map.of("CRITICAL", "CRITICAL", "HIGH", "HIGH", "MEDIUM", "MEDIUM", "LOW", "LOW"),
            "depcheck", Map.of("HIGH", "HIGH", "MEDIUM", "MEDIUM", "LOW", "LOW"),
            "secrets", Map.of("CRITICAL", "CRITICAL", "HIGH", "HIGH")
        ));
        return config;
    }

    @Bean
    public CompileScanner compileScanner() {
        return new CompileScanner();
    }

    @Bean
    public TestScanner testScanner() {
        return new TestScanner();
    }

    @Bean
    public CoverageScanner coverageScanner() {
        return new CoverageScanner();
    }

    @Bean
    public SpotBugsScanner spotBugsScanner() {
        return new SpotBugsScanner();
    }

    @Bean
    public CheckstyleScanner checkstyleScanner() {
        return new CheckstyleScanner();
    }

    @Bean
    public SemgrepScanner semgrepScanner() {
        return new SemgrepScanner();
    }

    @Bean
    public DepCheckScanner depCheckScanner() {
        return new DepCheckScanner();
    }

    @Bean
    public TrivyScanner trivyScanner() {
        return new TrivyScanner();
    }

    @Bean
    public SecretsScanner secretsScanner() {
        return new SecretsScanner();
    }

    @Bean
    public ScannerRegistry scannerRegistry(
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
        return new ScannerRegistry(
            compileScanner, testScanner, coverageScanner,
            spotBugsScanner, checkstyleScanner, semgrepScanner,
            depCheckScanner, trivyScanner, secretsScanner
        );
    }

    @Bean
    public VerificationRunner verificationRunner(SandboxService sandboxService, ScannerRegistry scannerRegistry, VerifyConfig config) {
        return new VerificationRunner(sandboxService, scannerRegistry, config);
    }
}