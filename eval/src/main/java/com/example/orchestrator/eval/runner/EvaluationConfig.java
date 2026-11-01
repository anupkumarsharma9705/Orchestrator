package com.example.orchestrator.eval.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration for the Evaluation Runner.
 */
@Component
@ConfigurationProperties(prefix = "orchestrator.eval")
@Validated
public class EvaluationConfig {

    @Positive
    @Max(10)
    private int maxParallelSpecs = 2;

    private String workspaceBase = "target/eval-workspace";

    private boolean generateReportCsv = true;
    private String reportOutputDir = "target/eval-reports";

    private int defaultTimeoutSec = 300;

    public int getMaxParallelSpecs() { return maxParallelSpecs; }
    public void setMaxParallelSpecs(int maxParallelSpecs) { this.maxParallelSpecs = maxParallelSpecs; }

    public String getWorkspaceBase() { return workspaceBase; }
    public void setWorkspaceBase(String workspaceBase) { this.workspaceBase = workspaceBase; }

    public boolean isGenerateReportCsv() { return generateReportCsv; }
    public void setGenerateReportCsv(boolean generateReportCsv) { this.generateReportCsv = generateReportCsv; }

    public String getReportOutputDir() { return reportOutputDir; }
    public void setReportOutputDir(String reportOutputDir) { this.reportOutputDir = reportOutputDir; }

    public int getDefaultTimeoutSec() { return defaultTimeoutSec; }
    public void setDefaultTimeoutSec(int defaultTimeoutSec) { this.defaultTimeoutSec = defaultTimeoutSec; }
}