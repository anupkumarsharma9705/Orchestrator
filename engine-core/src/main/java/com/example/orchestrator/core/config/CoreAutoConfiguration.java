package com.example.orchestrator.core.config;

import com.example.orchestrator.core.coder.Coder;
import com.example.orchestrator.core.coder.CoderConfig;
import com.example.orchestrator.core.coder.FileTools;
import com.example.orchestrator.core.clarifier.Clarifier;
import com.example.orchestrator.core.clarifier.ClarifierConfig;
import com.example.orchestrator.core.fixer.Fixer;
import com.example.orchestrator.core.fixer.FixerConfig;
import com.example.orchestrator.core.fixer.FixLoop;
import com.example.orchestrator.core.fixer.FixLoopConfig;
import com.example.orchestrator.core.hardening.HardeningAutoConfiguration;
import com.example.orchestrator.core.planner.Planner;
import com.example.orchestrator.core.planner.PlannerConfig;
import com.example.orchestrator.core.refiner.Refiner;
import com.example.orchestrator.core.refiner.RefinerConfig;
import com.example.orchestrator.core.state.StateManager;
import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.llm.config.ModelRouterAutoConfiguration;
import com.example.orchestrator.verify.runner.VerificationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the Core engine module.
 * Sets up Clarifier, Refiner, Planner, Coder, Fixer, FixLoop, Hardening, StateManager.
 */
@Configuration
@EnableConfigurationProperties({ClarifierConfig.class, RefinerConfig.class, PlannerConfig.class, CoderConfig.class, FixerConfig.class, FixLoopConfig.class})
@Import({ModelRouterAutoConfiguration.class, HardeningAutoConfiguration.class})
public class CoreAutoConfiguration {

    @Bean
    public com.example.orchestrator.core.clarifier.Clarifier clarifier(ModelRouter modelRouter, ClarifierConfig config) {
        return new com.example.orchestrator.core.clarifier.Clarifier(modelRouter, config);
    }

    @Bean
    public Refiner refiner(ModelRouter modelRouter, RefinerConfig config) {
        return new Refiner(modelRouter, config);
    }

    @Bean
    public Planner planner(ModelRouter modelRouter, PlannerConfig config) {
        return new Planner(modelRouter, config);
    }

    @Bean
    public FileTools fileTools() {
        return new FileTools();
    }

    @Bean
    public Coder coder(ModelRouter modelRouter, CoderConfig config, FileTools fileTools) {
        return new Coder(modelRouter, config, fileTools);
    }

    @Bean
    @ConditionalOnBean(VerificationRunner.class)
    public Fixer fixer(ModelRouter modelRouter, FileTools fileTools, FixerConfig config) {
        return new Fixer(modelRouter, fileTools, config);
    }

    @Bean
    @ConditionalOnBean(VerificationRunner.class)
    public FixLoop fixLoop(Fixer fixer, VerificationRunner verificationRunner, FileTools fileTools, FixLoopConfig config) {
        return new FixLoop(fixer, verificationRunner, fileTools, config);
    }

    @Bean
    public StateManager stateManager(com.example.orchestrator.store.ArtifactStore artifactStore, 
                                     com.example.orchestrator.core.hardening.CorrelationLogger correlationLogger) {
        return new StateManager(artifactStore, correlationLogger);
    }
}