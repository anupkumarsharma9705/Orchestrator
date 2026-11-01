package com.example.orchestrator.core.state;

import com.example.orchestrator.core.hardening.CorrelationLogger;
import com.example.orchestrator.model.JobState;
import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.RequirementsDoc;
import com.example.orchestrator.model.RoundReport;
import com.example.orchestrator.store.ArtifactStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persisted state for crash recovery and resume capability.
 * Saves state to .companion/state/ directory as JSON.
 */
@Component
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);

    private final ArtifactStore artifactStore;
    private final CorrelationLogger correlationLogger;
    private final ObjectMapper objectMapper;

    // In-memory cache for quick access
    private final Map<String, JobStateSnapshot> stateCache = new ConcurrentHashMap<>();

    public StateManager(ArtifactStore artifactStore, CorrelationLogger correlationLogger) {
        this.artifactStore = artifactStore;
        this.correlationLogger = correlationLogger;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Saves a complete job state snapshot for recovery.
     * Uses atomic write with file locking to prevent corruption.
     */
    public void saveState(String jobId, JobState state, RequirementsDoc requirements,
                          Plan plan, List<RoundReport> rounds, Instant lastUpdated) {
        JobStateSnapshot snapshot = new JobStateSnapshot(
            jobId,
            state,
            requirements,
            plan,
            rounds,
            lastUpdated,
            Instant.now()
        );

        // H6: Write to disk FIRST (atomic), then update cache
        persistToDisk(jobId, snapshot);
        stateCache.put(jobId, snapshot);
        
        log.debug("Saved state for job {}: {}", jobId, state);
    }

    /**
     * Loads a job state snapshot for recovery.
     */
    public Optional<JobStateSnapshot> loadState(String jobId) {
        // Check cache first
        if (stateCache.containsKey(jobId)) {
            return Optional.of(stateCache.get(jobId));
        }

        // Try loading from disk
        try {
            Path stateFile = getStateFile(jobId);
            if (Files.exists(stateFile)) {
                String json = Files.readString(stateFile);
                JobStateSnapshot snapshot = objectMapper.readValue(json, JobStateSnapshot.class);
                stateCache.put(jobId, snapshot);
                return Optional.of(snapshot);
            }
        } catch (Exception e) {
            log.warn("Failed to load state for job {}: {}", jobId, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Checks if a job can be resumed from a previous state.
     */
    public boolean canResume(String jobId) {
        Optional<JobStateSnapshot> snapshot = loadState(jobId);
        return snapshot.isPresent() && 
            snapshot.get().state() != JobState.COMPLETED &&
            snapshot.get().state() != JobState.FAILED &&
            snapshot.get().state() != JobState.CANCELLED;
    }

    /**
     * Gets the last completed round for a job.
     */
    public int getLastCompletedRound(String jobId) {
        return loadState(jobId)
            .map(s -> s.rounds().size())
            .orElse(0);
    }

    /**
     * Gets the last saved plan for a job.
     */
    public Optional<com.example.orchestrator.model.Plan> getSavedPlan(String jobId) {
        return loadState(jobId).map(JobStateSnapshot::plan);
    }

    /**
     * Gets the last saved requirements for a job.
     */
    public Optional<com.example.orchestrator.model.RequirementsDoc> getSavedRequirements(String jobId) {
        return loadState(jobId).map(JobStateSnapshot::requirements);
    }

    /**
     * Marks a job as completed successfully.
     */
    public void markCompleted(String jobId) {
        loadState(jobId).ifPresent(snapshot -> {
            JobStateSnapshot updated = new JobStateSnapshot(
                snapshot.jobId(),
                com.example.orchestrator.model.JobState.COMPLETED,
                snapshot.requirements(),
                snapshot.plan(),
                snapshot.rounds(),
                snapshot.lastUpdated(),
                Instant.now()
            );
            stateCache.put(jobId, updated);
            persistToDisk(jobId, updated);
        });
    }

    /**
     * Marks a job as failed with error.
     */
    public void markFailed(String jobId, String error) {
        loadState(jobId).ifPresent(snapshot -> {
            JobStateSnapshot updated = new JobStateSnapshot(
                snapshot.jobId(),
                com.example.orchestrator.model.JobState.FAILED,
                snapshot.requirements(),
                snapshot.plan(),
                snapshot.rounds(),
                snapshot.lastUpdated(),
                Instant.now()
            );
            stateCache.put(jobId, updated);
            persistToDisk(jobId, updated);
        });
    }

    /**
     * Cleans up state after successful completion (optional, for disk space).
     */
    public void cleanup(String jobId) {
        stateCache.remove(jobId);
        try {
            Path stateFile = getStateFile(jobId);
            if (Files.exists(stateFile)) {
                Files.delete(stateFile);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup state file for job {}: {}", jobId, e.getMessage());
        }
    }

    private Path getStateFile(String jobId) {
        Path baseDir = Paths.get(".companion", "state");
        return baseDir.resolve(jobId + ".json");
    }

    private void persistToDisk(String jobId, JobStateSnapshot snapshot) {
        try {
            Path stateFile = getStateFile(jobId);
            Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            Files.createDirectories(stateFile.getParent());
            
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
            
            // H6: Atomic write with file locking
            // Write to temp file first
            Files.writeString(tempFile, json);
            
            // Acquire file lock and atomically rename
            try (FileChannel channel = FileChannel.open(stateFile, 
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = channel.lock();
                try {
                    // Atomic rename from temp to final
                    Files.move(tempFile, stateFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    lock.release();
                }
            }
            
            log.debug("Persisted state for job {} to {}", jobId, stateFile);
        } catch (Exception e) {
            log.warn("Failed to persist state for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Snapshot of job state for recovery.
     */
    public record JobStateSnapshot(
        String jobId,
        com.example.orchestrator.model.JobState state,
        com.example.orchestrator.model.RequirementsDoc requirements,
        com.example.orchestrator.model.Plan plan,
        List<com.example.orchestrator.model.RoundReport> rounds,
        Instant lastUpdated,
        Instant savedAt
    ) {}
}