package com.example.orchestrator.verify;

import com.example.orchestrator.sandbox.SandboxService;
import com.example.orchestrator.sandbox.SandboxAutoConfiguration;
import com.example.orchestrator.verify.runner.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the Verifier module.
 */
@Configuration
@EnableConfigurationProperties(VerifyConfig.class)
@Import(SandboxAutoConfiguration.class)
@ConditionalOnProperty(name = "orchestrator.verify.sandbox.enabled", havingValue = "true", matchIfMissing = true)
public class VerifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VerificationRunner verificationRunner(SandboxService sandboxService, ScannerRegistry scannerRegistry, VerifyConfig config) {
        return new VerificationRunner(sandboxService, scannerRegistry, config);
    }

    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    public CompileScanner compileScanner() {
        return new CompileScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public TestScanner testScanner() {
        return new TestScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public CoverageScanner coverageScanner() {
        return new CoverageScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpotBugsScanner spotBugsScanner() {
        return new SpotBugsScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public CheckstyleScanner checkstyleScanner() {
        return new CheckstyleScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public SemgrepScanner semgrepScanner() {
        return new SemgrepScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public DepCheckScanner depCheckScanner() {
        return new DepCheckScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public TrivyScanner trivyScanner() {
        return new TrivyScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecretsScanner secretsScanner() {
        return new SecretsScanner();
    }
}