package com.example.orchestrator;

import com.example.orchestrator.model.router.CostTracker;
import com.example.orchestrator.model.router.ModelRouterConfig;
import com.example.orchestrator.persistence.repo.CostAggregateRepository;
import com.example.orchestrator.persistence.repo.DailyCostRepository;
import com.example.orchestrator.persistence.repo.TaskCostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public TaskCostRepository taskCostRepository() {
        return org.mockito.Mockito.mock(TaskCostRepository.class);
    }

    @Bean
    @Primary
    public DailyCostRepository dailyCostRepository() {
        return org.mockito.Mockito.mock(DailyCostRepository.class);
    }

    @Bean
    @Primary
    public CostAggregateRepository costAggregateRepository() {
        return org.mockito.Mockito.mock(CostAggregateRepository.class);
    }

    @Bean
    @Primary
    public ModelRouterConfig modelRouterConfig() {
        ModelRouterConfig config = ModelRouterConfig.defaultConfig();
        return config;
    }

    @Bean
    @Primary
    public CostTracker costTracker(
            @Autowired ModelRouterConfig config,
            @Autowired TaskCostRepository taskCostRepository,
            @Autowired DailyCostRepository dailyCostRepository,
            @Autowired CostAggregateRepository costAggregateRepository) {
        return new CostTracker(config, taskCostRepository, dailyCostRepository, costAggregateRepository);
    }
}