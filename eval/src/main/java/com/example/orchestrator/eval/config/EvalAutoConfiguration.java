package com.example.orchestrator.eval.config;

import com.example.orchestrator.eval.runner.CsvReportWriter;
import com.example.orchestrator.eval.runner.EvaluationConfig;
import com.example.orchestrator.eval.runner.EvaluationRunner;
import com.example.orchestrator.eval.runner.SpecLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Evaluation module.
 */
@Configuration
@EnableConfigurationProperties(EvaluationConfig.class)
public class EvalAutoConfiguration {

    @Bean
    public SpecLoader specLoader() {
        return new SpecLoader();
    }

    @Bean
    public EvaluationRunner evaluationRunner(
            com.example.orchestrator.llm.ModelRouter modelRouter,
            com.example.orchestrator.templates.TemplateEngine templateEngine,
            com.example.orchestrator.verify.runner.VerificationRunner verificationRunner,
            com.example.orchestrator.core.fixer.FixLoop fixLoop,
            com.example.orchestrator.core.coder.Coder coder,
            EvaluationConfig config) {
        return new EvaluationRunner(modelRouter, templateEngine, verificationRunner, fixLoop, coder, config);
    }

    @Bean
    public CsvReportWriter csvReportWriter() {
        return new CsvReportWriter();
    }
}