package com.example.orchestrator.core.fixer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration for the Five-Round Fix Loop.
 */
@ConfigurationProperties(prefix = "orchestrator.fixloop")
@Validated
public class FixLoopConfig {

    @Positive
    @Max(10)
    private int maxRounds = 5;

    @Positive
    private int maxFixesPerRound = 20;

    @Min(1)
    private int regressionRetryLimit = 1;

    private boolean stopOnNoHighCritical = true;
    private boolean stopOnNoProgress = true;
    private boolean stopOnBudgetExceeded = true;

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }

    public int getMaxFixesPerRound() { return maxFixesPerRound; }
    public void setMaxFixesPerRound(int maxFixesPerRound) { this.maxFixesPerRound = maxFixesPerRound; }

    public int getRegressionRetryLimit() { return regressionRetryLimit; }
    public void setRegressionRetryLimit(int regressionRetryLimit) { this.regressionRetryLimit = regressionRetryLimit; }

    public boolean isStopOnNoHighCritical() { return stopOnNoHighCritical; }
    public void setStopOnNoHighCritical(boolean stopOnNoHighCritical) { this.stopOnNoHighCritical = stopOnNoHighCritical; }

    public boolean isStopOnNoProgress() { return stopOnNoProgress; }
    public void setStopOnNoProgress(boolean stopOnNoProgress) { this.stopOnNoProgress = stopOnNoProgress; }

    public boolean isStopOnBudgetExceeded() { return stopOnBudgetExceeded; }
    public void setStopOnBudgetExceeded(boolean stopOnBudgetExceeded) { this.stopOnBudgetExceeded = stopOnBudgetExceeded; }
}
