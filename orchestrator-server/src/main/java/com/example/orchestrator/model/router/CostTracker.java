package com.example.orchestrator.model.router;

import com.example.orchestrator.persistence.entity.CostAggregateEntity;
import com.example.orchestrator.persistence.entity.DailyCostEntity;
import com.example.orchestrator.persistence.entity.TaskCostEntity;
import com.example.orchestrator.persistence.repo.CostAggregateRepository;
import com.example.orchestrator.persistence.repo.DailyCostRepository;
import com.example.orchestrator.persistence.repo.TaskCostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks token usage and estimated cost per task and per day.
 *
 * <p>Cost data is written through to the database so that budget enforcement
 * survives restarts. The in-memory values remain the hot path; persistence
 * failures are logged and do not break an LLM request.</p>
 */
@Component("serverCostTracker")
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);
    private static final String AGGREGATE_ID = "default";
    private static final double MICROS_PER_USD = 1_000_000.0;

    private final boolean enabled;
    private final double budgetPerTaskUsd;
    private final double budgetPerDayUsd;
    private final double alertThresholdPercent;

    private final TaskCostRepository taskCostRepository;
    private final DailyCostRepository dailyCostRepository;
    private final CostAggregateRepository costAggregateRepository;

    private final Object lock = new Object();
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, TaskCost> taskCosts = new ConcurrentHashMap<>();
    private final AtomicReference<DailyCost> dailyCost =
            new AtomicReference<>(new DailyCost(0, 0, 0, Instant.now().toEpochMilli()));
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalCostUsdMicro = new AtomicLong(0);
    private final AtomicLong dailyCostUsdMicro = new AtomicLong(0);

    private LocalDate dailyDate = LocalDate.now();
    private Instant lastDailyReset = Instant.now();

    public CostTracker(ModelRouterConfig config,
                       TaskCostRepository taskCostRepository,
                       DailyCostRepository dailyCostRepository,
                       CostAggregateRepository costAggregateRepository) {
        ModelRouterConfig.CostTrackingConfig costTracking = config.costTracking();
        this.enabled = costTracking != null && Boolean.TRUE.equals(costTracking.enabled());
        this.budgetPerTaskUsd = costTracking != null && costTracking.budgetPerTaskUsd() != null
                ? costTracking.budgetPerTaskUsd() : 0.50;
        this.budgetPerDayUsd = costTracking != null && costTracking.budgetPerDayUsd() != null
                ? costTracking.budgetPerDayUsd() : 10.0;
        this.alertThresholdPercent = costTracking != null && costTracking.alertThresholdPercent() != null
                ? costTracking.alertThresholdPercent() : 80.0;
        this.taskCostRepository = taskCostRepository;
        this.dailyCostRepository = dailyCostRepository;
        this.costAggregateRepository = costAggregateRepository;
    }

    /**
     * Records one model call and persists the updated task, daily and aggregate totals.
     */
    public void recordUsage(String taskId, String model, String provider,
                            long promptTokens, long completionTokens, double costUsd) {
        if (!enabled) {
            return;
        }

        ensureLoaded();

        String safeTaskId = taskId == null || taskId.isBlank() ? "unknown" : taskId;
        long safePromptTokens = Math.max(0, promptTokens);
        long safeCompletionTokens = Math.max(0, completionTokens);
        long costMicro = Math.max(0, Math.round(costUsd * MICROS_PER_USD));
        Instant now = Instant.now();

        synchronized (lock) {
            rolloverIfNeeded(now);

            TaskCost taskCost = getTaskCost(safeTaskId);
            if (taskCost == null) {
                TaskCost fresh = new TaskCost(safeTaskId, model, provider, 0, 0, 0.0, now, now);
                TaskCost existing = taskCosts.putIfAbsent(safeTaskId, fresh);
                taskCost = existing != null ? existing : fresh;
            }
            taskCost.addUsage(safePromptTokens, safeCompletionTokens, costMicro, now);
            if (model != null) {
                taskCost.setModel(model);
            }
            if (provider != null) {
                taskCost.setProvider(provider);
            }

            totalPromptTokens.addAndGet(safePromptTokens);
            totalCompletionTokens.addAndGet(safeCompletionTokens);
            totalCostUsdMicro.addAndGet(costMicro);
            dailyCostUsdMicro.addAndGet(costMicro);

            DailyCost current = dailyCost.get();
            dailyCost.set(new DailyCost(
                    current.getPromptTokens() + safePromptTokens,
                    current.getCompletionTokens() + safeCompletionTokens,
                    current.getCostMicro() + costMicro,
                    now.toEpochMilli()
            ));

            persistTaskCost(taskCost);
            persistDailyCost(now);
            persistAggregate(now);
        }

        checkBudgetAlerts();
    }

    public boolean isOverTaskBudget(String taskId) {
        if (!enabled) {
            return false;
        }
        TaskCost cost = getTaskCost(taskId);
        return cost != null && cost.getCostUsd() > budgetPerTaskUsd;
    }

    public boolean isOverDailyBudget() {
        if (!enabled) {
            return false;
        }
        ensureLoaded();
        synchronized (lock) {
            rolloverIfNeeded(Instant.now());
            return dailyCostUsdMicro.get() / MICROS_PER_USD > budgetPerDayUsd;
        }
    }

    public boolean isNearTaskBudget(String taskId) {
        if (!enabled) {
            return false;
        }
        TaskCost cost = getTaskCost(taskId);
        return cost != null
                && cost.getCostUsd() >= budgetPerTaskUsd * (alertThresholdPercent / 100.0);
    }

    public boolean isNearDailyBudget() {
        if (!enabled) {
            return false;
        }
        ensureLoaded();
        synchronized (lock) {
            rolloverIfNeeded(Instant.now());
            return dailyCostUsdMicro.get() / MICROS_PER_USD
                    >= budgetPerDayUsd * (alertThresholdPercent / 100.0);
        }
    }

    public TaskCost getTaskCost(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        ensureLoaded();

        TaskCost cached = taskCosts.get(taskId);
        if (cached != null) {
            return cached;
        }

        try {
            Optional<TaskCostEntity> entity = taskCostRepository
                    .findFirstByTaskIdOrderByLastUpdateDesc(taskId);
            if (entity == null || entity.isEmpty()) {
                return null;
            }
            TaskCost loadedCost = fromEntity(entity.get());
            taskCosts.put(taskId, loadedCost);
            return loadedCost;
        } catch (RuntimeException e) {
            log.warn("Failed to load cost for task {}: {}", taskId, e.getMessage());
            return null;
        }
    }

    public DailyCost getDailyCost() {
        ensureLoaded();
        synchronized (lock) {
            rolloverIfNeeded(Instant.now());
            return dailyCost.get();
        }
    }

    public long getTotalPromptTokens() {
        ensureLoaded();
        return totalPromptTokens.get();
    }

    public long getTotalCompletionTokens() {
        ensureLoaded();
        return totalCompletionTokens.get();
    }

    public double getTotalCostUsd() {
        ensureLoaded();
        return totalCostUsdMicro.get() / MICROS_PER_USD;
    }

    public double getDailyCostUsd() {
        ensureLoaded();
        synchronized (lock) {
            rolloverIfNeeded(Instant.now());
            return dailyCostUsdMicro.get() / MICROS_PER_USD;
        }
    }

    public void resetDaily() {
        ensureLoaded();
        Instant now = Instant.now();
        synchronized (lock) {
            resetDailyState(now);
            persistDailyCost(now);
            persistAggregate(now);
        }
    }

    public double getBudgetPerTaskUsd() {
        return budgetPerTaskUsd;
    }

    public double getBudgetPerDayUsd() {
        return budgetPerDayUsd;
    }

    public double getAlertThresholdPercent() {
        return alertThresholdPercent;
    }

    private void ensureLoaded() {
        if (loaded.get()) {
            return;
        }

        synchronized (lock) {
            if (loaded.get()) {
                return;
            }

            Instant now = Instant.now();
            try {
                LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
                Optional<CostAggregateEntity> aggregate = costAggregateRepository.findById(AGGREGATE_ID);
                if (aggregate != null && aggregate.isPresent()) {
                    CostAggregateEntity entity = aggregate.get();
                    totalPromptTokens.set(entity.getTotalPromptTokens());
                    totalCompletionTokens.set(entity.getTotalCompletionTokens());
                    totalCostUsdMicro.set(entity.getTotalCostUsdMicro());
                    dailyCostUsdMicro.set(entity.getDailyCostUsdMicro());
                    lastDailyReset = entity.getLastReset() != null ? entity.getLastReset() : now;
                    dailyDate = lastDailyReset.atZone(ZoneOffset.UTC).toLocalDate();
                }

                Optional<DailyCostEntity> todayCost = dailyCostRepository.findByDate(today);
                if (todayCost != null && todayCost.isPresent()) {
                    DailyCostEntity entity = todayCost.get();
                    dailyDate = today;
                    lastDailyReset = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now;
                    dailyCostUsdMicro.set(entity.getCostUsdMicro());
                    dailyCost.set(new DailyCost(
                            entity.getPromptTokens(),
                            entity.getCompletionTokens(),
                            entity.getCostUsdMicro(),
                            entity.getUpdatedAt() != null
                                    ? entity.getUpdatedAt().toEpochMilli() : now.toEpochMilli()
                    ));
                } else if (!today.equals(dailyDate)) {
                    dailyCostUsdMicro.set(0);
                }

                rolloverIfNeeded(now);
            } catch (RuntimeException e) {
                // Cost tracking is observability/budgeting, not a reason to fail a request.
                log.warn("Failed to load persisted cost data: {}", e.getMessage());
            } finally {
                loaded.set(true);
            }
        }
    }

    private void rolloverIfNeeded(Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (today.equals(dailyDate)) {
            return;
        }

        resetDailyState(now);
        persistDailyCost(now);
        persistAggregate(now);
    }

    private void resetDailyState(Instant now) {
        dailyDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        lastDailyReset = now;
        dailyCostUsdMicro.set(0);
        dailyCost.set(new DailyCost(0, 0, 0, now.toEpochMilli()));
    }

    private void persistTaskCost(TaskCost cost) {
        if (taskCostRepository == null) {
            return;
        }
        try {
            taskCostRepository.save(new TaskCostEntity(
                    cost.getTaskId(),
                    cost.getTaskId(),
                    cost.getModel(),
                    cost.getProvider(),
                    cost.getPromptTokens(),
                    cost.getCompletionTokens(),
                    cost.getCostMicro(),
                    cost.getStartTime(),
                    cost.getLastUpdate()
            ));
        } catch (RuntimeException e) {
            log.warn("Failed to persist cost for task {}: {}", cost.getTaskId(), e.getMessage());
        }
    }

    private void persistDailyCost(Instant now) {
        if (dailyCostRepository == null) {
            return;
        }
        try {
            DailyCost cost = dailyCost.get();
            dailyCostRepository.save(new DailyCostEntity(
                    dailyDate,
                    cost.getPromptTokens(),
                    cost.getCompletionTokens(),
                    cost.getCostMicro(),
                    now
            ));
        } catch (RuntimeException e) {
            log.warn("Failed to persist daily cost: {}", e.getMessage());
        }
    }

    private void persistAggregate(Instant now) {
        if (costAggregateRepository == null) {
            return;
        }
        try {
            costAggregateRepository.save(new CostAggregateEntity(
                    AGGREGATE_ID,
                    totalPromptTokens.get(),
                    totalCompletionTokens.get(),
                    totalCostUsdMicro.get(),
                    dailyCostUsdMicro.get(),
                    lastDailyReset,
                    now
            ));
        } catch (RuntimeException e) {
            log.warn("Failed to persist cost aggregate: {}", e.getMessage());
        }
    }

    private TaskCost fromEntity(TaskCostEntity entity) {
        return new TaskCost(
                entity.getTaskId(),
                entity.getModel(),
                entity.getProvider(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getCostUsdMicro() / MICROS_PER_USD,
                entity.getStartTime() != null ? entity.getStartTime() : Instant.now(),
                entity.getLastUpdate() != null ? entity.getLastUpdate() : Instant.now()
        );
    }

    private void checkBudgetAlerts() {
        if (isOverDailyBudget()) {
            log.warn("Daily cost budget exceeded: ${}/{}",
                    String.format("%.4f", getDailyCostUsd()),
                    String.format("%.2f", budgetPerDayUsd));
        } else if (isNearDailyBudget()) {
            log.warn("Daily cost budget at {}% threshold: ${}/{}",
                    alertThresholdPercent,
                    String.format("%.4f", getDailyCostUsd()),
                    String.format("%.2f", budgetPerDayUsd));
        }
    }

    public static class TaskCost {
        private final String taskId;
        private String model;
        private String provider;
        private long promptTokens;
        private long completionTokens;
        private long costMicro;
        private final Instant startTime;
        private Instant lastUpdate;

        public TaskCost() {
            this("", null, null, 0, 0, 0.0, Instant.now(), Instant.now());
        }

        public TaskCost(String taskId, long promptTokens, long completionTokens,
                        double costUsd, Instant startTime, Instant lastUpdate) {
            this(taskId, null, null, promptTokens, completionTokens, costUsd, startTime, lastUpdate);
        }

        public TaskCost(String taskId, String model, String provider,
                        long promptTokens, long completionTokens, double costUsd,
                        Instant startTime, Instant lastUpdate) {
            this.taskId = taskId;
            this.model = model;
            this.provider = provider;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.costMicro = Math.max(0, Math.round(costUsd * MICROS_PER_USD));
            this.startTime = startTime;
            this.lastUpdate = lastUpdate;
        }

        private void addUsage(long promptTokens, long completionTokens,
                              long costMicro, Instant now) {
            this.promptTokens += promptTokens;
            this.completionTokens += completionTokens;
            this.costMicro += costMicro;
            this.lastUpdate = now;
        }

        private void setModel(String model) {
            this.model = model;
        }

        private void setProvider(String provider) {
            this.provider = provider;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getModel() {
            return model;
        }

        public String getProvider() {
            return provider;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }

        public double getCostUsd() {
            return costMicro / MICROS_PER_USD;
        }

        private long getCostMicro() {
            return costMicro;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getLastUpdate() {
            return lastUpdate;
        }
    }

    public static class DailyCost {
        private final long promptTokens;
        private final long completionTokens;
        private final long costMicro;
        private final long timestamp;

        public DailyCost() {
            this(0, 0, 0, Instant.now().toEpochMilli());
        }

        public DailyCost(long promptTokens, long completionTokens, long timestamp) {
            this(promptTokens, completionTokens, 0, timestamp);
        }

        public DailyCost(long promptTokens, long completionTokens, long costMicro, long timestamp) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.costMicro = costMicro;
            this.timestamp = timestamp;
        }

        public double costUsd() {
            return costMicro / MICROS_PER_USD;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }

        private long getCostMicro() {
            return costMicro;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
