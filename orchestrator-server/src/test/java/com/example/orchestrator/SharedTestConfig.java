package com.example.orchestrator;

import com.example.orchestrator.model.router.CostTracker;
import com.example.orchestrator.model.router.ModelRouterConfig;
import com.example.orchestrator.persistence.repo.CostAggregateRepository;
import com.example.orchestrator.persistence.repo.DailyCostRepository;
import com.example.orchestrator.persistence.repo.TaskCostRepository;
import com.example.orchestrator.permission.PermissionManager;
import com.example.orchestrator.project.ProjectManager;
import com.example.orchestrator.repair.RepairLoopService;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.verification.VerificationController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SharedTestConfig {

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
        return ModelRouterConfig.defaultConfig();
    }

    @Bean
    @Primary
    public CostTracker costTracker(
            ModelRouterConfig config,
            TaskCostRepository taskCostRepository,
            DailyCostRepository dailyCostRepository,
            CostAggregateRepository costAggregateRepository) {
        return new CostTracker(config, taskCostRepository, dailyCostRepository, costAggregateRepository);
    }

    @Bean
    @Primary
    public PermissionManager permissionManager() {
        return org.mockito.Mockito.mock(PermissionManager.class);
    }

    @Bean
    @Primary
    public ProjectManager projectManager() {
        return org.mockito.Mockito.mock(ProjectManager.class);
    }

    @Bean
    @Primary
    public RepairLoopService repairLoopService() {
        return org.mockito.Mockito.mock(RepairLoopService.class);
    }

    @Bean
    @Primary
    public ToolRegistry toolRegistry() {
        return org.mockito.Mockito.mock(ToolRegistry.class);
    }

    @Bean
    @Primary
    public VerificationController verificationController() {
        return org.mockito.Mockito.mock(VerificationController.class);
    }
}