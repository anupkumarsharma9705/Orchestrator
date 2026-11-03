package com.example.orchestrator.model.router;

import com.example.orchestrator.persistence.entity.CostAggregateEntity;
import com.example.orchestrator.persistence.entity.DailyCostEntity;
import com.example.orchestrator.persistence.entity.TaskCostEntity;
import com.example.orchestrator.persistence.repo.CostAggregateRepository;
import com.example.orchestrator.persistence.repo.DailyCostRepository;
import com.example.orchestrator.persistence.repo.TaskCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves that {@link CostTracker} writes cost data through to the database and
 * restores it after a restart. Budget enforcement is only meaningful if the
 * totals survive a process restart, so this is deliberately not an
 * in-memory-only test.
 */
class CostTrackerPersistenceTest {

    private TaskCostRepository taskCostRepository;
    private DailyCostRepository dailyCostRepository;
    private CostAggregateRepository costAggregateRepository;

    private final AtomicReference<TaskCostEntity> savedTask = new AtomicReference<>();
    private final AtomicReference<DailyCostEntity> savedDaily = new AtomicReference<>();
    private final AtomicReference<CostAggregateEntity> savedAggregate = new AtomicReference<>();

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        taskCostRepository = mock(TaskCostRepository.class);
        dailyCostRepository = mock(DailyCostRepository.class);
        costAggregateRepository = mock(CostAggregateRepository.class);

        when(costAggregateRepository.findById("default")).thenReturn(Optional.empty());
        when(dailyCostRepository.findByDate(today)).thenReturn(Optional.empty());

        when(taskCostRepository.save(any(TaskCostEntity.class))).thenAnswer(invocation -> {
            TaskCostEntity entity = invocation.getArgument(0);
            savedTask.set(entity);
            return entity;
        });
        when(dailyCostRepository.save(any(DailyCostEntity.class))).thenAnswer(invocation -> {
            DailyCostEntity entity = invocation.getArgument(0);
            savedDaily.set(entity);
            return entity;
        });
        when(costAggregateRepository.save(any(CostAggregateEntity.class))).thenAnswer(invocation -> {
            CostAggregateEntity entity = invocation.getArgument(0);
            savedAggregate.set(entity);
            return entity;
        });
    }

    @Test
    void recordUsageWritesTaskDailyAndAggregateRows() {
        CostTracker tracker = newTracker();

        tracker.recordUsage("TASK-1", "model-x", "provider-y", 100, 50, 0.0123);

        TaskCostEntity task = savedTask.get();
        assertNotNull(task, "task cost row must be written");
        assertEquals("TASK-1", task.getId());
        assertEquals("TASK-1", task.getTaskId());
        assertEquals("model-x", task.getModel());
        assertEquals("provider-y", task.getProvider());
        assertEquals(100, task.getPromptTokens());
        assertEquals(50, task.getCompletionTokens());
        assertEquals(12_300, task.getCostUsdMicro());

        DailyCostEntity daily = savedDaily.get();
        assertNotNull(daily, "daily cost row must be written");
        assertEquals(today, daily.getDate());
        assertEquals(100, daily.getPromptTokens());
        assertEquals(50, daily.getCompletionTokens());
        assertEquals(12_300, daily.getCostUsdMicro());

        CostAggregateEntity aggregate = savedAggregate.get();
        assertNotNull(aggregate, "aggregate row must be written");
        assertEquals("default", aggregate.getId());
        assertEquals(100, aggregate.getTotalPromptTokens());
        assertEquals(50, aggregate.getTotalCompletionTokens());
        assertEquals(12_300, aggregate.getTotalCostUsdMicro());
        assertEquals(12_300, aggregate.getDailyCostUsdMicro());

        // The in-memory view must match what was persisted.
        assertEquals(100, tracker.getTotalPromptTokens());
        assertEquals(50, tracker.getTotalCompletionTokens());
        assertEquals(0.0123, tracker.getTotalCostUsd(), 1e-9);
        assertEquals(0.0123, tracker.getDailyCostUsd(), 1e-9);
    }

    @Test
    void newTrackerReloadsPersistedTotalsAndTaskCost() {
        Instant now = Instant.now();
        CostAggregateEntity aggregate = new CostAggregateEntity(
                "default", 1_000, 500, 25_000, 10_000, now, now);
        when(costAggregateRepository.findById("default")).thenReturn(Optional.of(aggregate));

        DailyCostEntity daily = new DailyCostEntity(today, 400, 200, 10_000, now);
        when(dailyCostRepository.findByDate(today)).thenReturn(Optional.of(daily));

        TaskCostEntity task = new TaskCostEntity(
                "TASK-9", "TASK-9", "model-z", "provider-z", 300, 100, 5_000, now, now);
        when(taskCostRepository.findFirstByTaskIdOrderByLastUpdateDesc("TASK-9"))
                .thenReturn(Optional.of(task));

        CostTracker tracker = newTracker();

        assertEquals(1_000, tracker.getTotalPromptTokens());
        assertEquals(500, tracker.getTotalCompletionTokens());
        assertEquals(0.025, tracker.getTotalCostUsd(), 1e-9);
        assertEquals(0.010, tracker.getDailyCostUsd(), 1e-9);

        CostTracker.TaskCost restored = tracker.getTaskCost("TASK-9");
        assertNotNull(restored, "task cost must be restored from the database");
        assertEquals(300, restored.getPromptTokens());
        assertEquals(100, restored.getCompletionTokens());
        assertEquals(0.005, restored.getCostUsd(), 1e-9);
        assertEquals("model-z", restored.getModel());
    }

    @Test
    void usageAfterReloadAccumulatesAndPersistsNewTotals() {
        Instant now = Instant.now();
        when(costAggregateRepository.findById("default")).thenReturn(Optional.of(
                new CostAggregateEntity("default", 1_000, 500, 25_000, 10_000, now, now)));
        when(dailyCostRepository.findByDate(today)).thenReturn(Optional.of(
                new DailyCostEntity(today, 400, 200, 10_000, now)));
        when(taskCostRepository.findFirstByTaskIdOrderByLastUpdateDesc("TASK-9")).thenReturn(Optional.of(
                new TaskCostEntity("TASK-9", "TASK-9", "model-z", "provider-z",
                        300, 100, 5_000, now, now)));

        CostTracker tracker = newTracker();
        tracker.recordUsage("TASK-9", "model-z", "provider-z", 100, 25, 0.001);

        assertEquals(1_100, tracker.getTotalPromptTokens());
        assertEquals(525, tracker.getTotalCompletionTokens());
        assertEquals(0.026, tracker.getTotalCostUsd(), 1e-9);
        assertEquals(0.011, tracker.getDailyCostUsd(), 1e-9);

        CostTracker.TaskCost task = tracker.getTaskCost("TASK-9");
        assertNotNull(task);
        assertEquals(400, task.getPromptTokens());
        assertEquals(125, task.getCompletionTokens());
        assertEquals(0.006, task.getCostUsd(), 1e-9);

        assertNotNull(savedAggregate.get());
        assertEquals(1_100, savedAggregate.get().getTotalPromptTokens());
        assertEquals(26_000, savedAggregate.get().getTotalCostUsdMicro());
        assertEquals(11_000, savedAggregate.get().getDailyCostUsdMicro());

        assertNotNull(savedDaily.get());
        assertEquals(500, savedDaily.get().getPromptTokens());
        assertEquals(11_000, savedDaily.get().getCostUsdMicro());

        assertNotNull(savedTask.get());
        assertEquals(6_000, savedTask.get().getCostUsdMicro());
    }

    @Test
    void persistenceFailureDoesNotBreakUsageTracking() {
        when(taskCostRepository.save(any(TaskCostEntity.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        CostTracker tracker = newTracker();

        // Must not throw: cost tracking must never fail an LLM request.
        tracker.recordUsage("TASK-2", "model-x", "provider-y", 10, 5, 0.0001);

        assertEquals(10, tracker.getTotalPromptTokens());
        assertEquals(5, tracker.getTotalCompletionTokens());
        assertEquals(0.0001, tracker.getTotalCostUsd(), 1e-9);
    }

    private CostTracker newTracker() {
        return new CostTracker(
                ModelRouterConfig.defaultConfig(),
                taskCostRepository,
                dailyCostRepository,
                costAggregateRepository);
    }
}
