package com.example.orchestrator.store;

import com.example.orchestrator.model.Finding;
import com.example.orchestrator.model.Fix;
import com.example.orchestrator.model.Job;
import com.example.orchestrator.model.JobState;
import com.example.orchestrator.model.RoundReport;
import com.example.orchestrator.store.entity.*;
import com.example.orchestrator.store.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Artifact Store - persists job execution artifacts to both files and database.
 * 
 * File artifacts (in .companion/):
 * - requirements.md
 * - plan.md
 * - fixes.md
 * - vulnerabilities.md
 * - report.md
 * - runs/ (JSON logs per round)
 * 
 * Database (SQLite):
 * - Jobs, Rounds, Findings, Costs
 */
@Service
public class ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStore.class);

    private final JobRepository jobRepository;
    private final RoundRepository roundRepository;
    private final FindingRepository findingRepository;
    private final CostRepository costRepository;

    @Value("${orchestrator.store.base-path:.companion}")
    private String basePath;

    public ArtifactStore(JobRepository jobRepository,
                         RoundRepository roundRepository,
                         FindingRepository findingRepository,
                         CostRepository costRepository) {
        this.jobRepository = jobRepository;
        this.roundRepository = roundRepository;
        this.findingRepository = findingRepository;
        this.costRepository = costRepository;
    }

    /**
     * Creates a new job record.
     */
    @Transactional
    public JobEntity createJob(Job job) {
        JobEntity entity = new JobEntity();
        entity.setId(job.id());
        entity.setProjectName(job.projectId()); // Job has projectId
        entity.setDescription("");
        entity.setStatus(job.state().name());
        entity.setCreatedAt(job.createdAt());
        entity.setStartedAt(null);
        entity.setFinishedAt(job.finishedAt());
        entity.setTotalCostUsd(job.costUsd());
        entity.setTotalDurationSec(0);
        entity.setConfigJson("{}");
        
        JobEntity saved = jobRepository.save(entity);
        log.info("Created job: {} ({})", saved.getProjectName(), saved.getId());
        return saved;
    }

    /**
     * Updates job status.
     */
    @Transactional
    public void updateJobStatus(String jobId, JobState status) {
        jobRepository.findById(jobId).ifPresent(entity -> {
            entity.setStatus(status.name());
            if ((status == JobState.BUILDING || status == JobState.VERIFYING || status == JobState.FIXING) 
                && entity.getStartedAt() == null) {
                entity.setStartedAt(Instant.now());
            }
            if (status == JobState.COMPLETED || status == JobState.FAILED || status == JobState.CANCELLED) {
                entity.setFinishedAt(Instant.now());
            }
            jobRepository.save(entity);
        });
    }

    /**
     * Updates job cost and duration.
     */
    @Transactional
    public void updateJobCostAndDuration(String jobId, BigDecimal costUsd, Integer durationSec) {
        jobRepository.findById(jobId).ifPresent(entity -> {
            entity.setTotalCostUsd(entity.getTotalCostUsd().add(costUsd));
            entity.setTotalDurationSec(entity.getTotalDurationSec() + durationSec);
            jobRepository.save(entity);
        });
    }

    /**
     * Saves a round report to database.
     */
    @Transactional
    public RoundEntity saveRound(RoundReport round, String jobId) {
        RoundEntity entity = new RoundEntity();
        entity.setId(round.id());
        entity.setJobId(jobId);
        entity.setRoundNum(round.roundNum());
        entity.setBuildOk(round.buildOk());
        entity.setTestsPassed(round.testsPassed());
        entity.setTestsFailed(round.testsFailed());
        entity.setCoverage(round.coverage());
        entity.setCostUsd(round.costUsd());
        entity.setDurationSec(round.durationSec());
        entity.setStartedAt(round.startedAt());
        entity.setFinishedAt(round.finishedAt());
        entity.setMetadataJson("{}");

        RoundEntity saved = roundRepository.save(entity);

        // Save findings
        if (round.findings() != null) {
            for (Finding finding : round.findings()) {
                saveFinding(finding, saved.getId());
            }
        }

        log.info("Saved round {} for job {}", round.roundNum(), jobId);
        return saved;
    }

    /**
     * Saves a finding to database.
     */
    @Transactional
    public FindingEntity saveFinding(Finding finding, String roundId) {
        FindingEntity entity = new FindingEntity();
        entity.setId(finding.fingerprint()); // Use fingerprint as ID
        entity.setRoundId(roundId);
        entity.setFingerprint(finding.fingerprint());
        entity.setTool(finding.tool());
        entity.setRuleId(finding.ruleId());
        entity.setSeverity(finding.severity().name());
        entity.setFilePath(finding.file());
        entity.setLineNumber(finding.line());
        entity.setMessage(finding.message());
        entity.setCodeSnippet(""); // Not in model Finding
        entity.setStatus(finding.status().name());
        entity.setFirstSeenRound(finding.firstSeenRound());
        entity.setFixedInRound(finding.fixedInRound());
        entity.setCreatedAt(finding.createdAt());

        FindingEntity saved = findingRepository.save(entity);
        return saved;
    }

    /**
     * Saves a cost record.
     */
    @Transactional
    public CostEntity saveCost(String jobId, String roundId, String taskId,
                               String profile, String provider, String model,
                               Integer inputTokens, Integer outputTokens,
                               BigDecimal costUsd) {
        CostEntity entity = new CostEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setJobId(jobId);
        entity.setRoundId(roundId);
        entity.setTaskId(taskId);
        entity.setProfile(profile);
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setInputTokens(inputTokens);
        entity.setOutputTokens(outputTokens);
        entity.setCostUsd(costUsd);
        entity.setCreatedAt(Instant.now());

        CostEntity saved = costRepository.save(entity);
        
        // Update job total cost
        updateJobCostAndDuration(jobId, costUsd, 0);
        
        return saved;
    }

    /**
     * Writes file artifacts for a job.
     */
    public void writeArtifacts(String jobId, String projectName,
                               String requirementsMd, String planMd,
                               String fixesMd, String vulnerabilitiesMd,
                               String reportMd) throws IOException {
        Path jobDir = Paths.get(basePath, projectName, jobId);
        Files.createDirectories(jobDir);

        writeIfNotNull(jobDir.resolve("requirements.md"), requirementsMd);
        writeIfNotNull(jobDir.resolve("plan.md"), planMd);
        writeIfNotNull(jobDir.resolve("fixes.md"), fixesMd);
        writeIfNotNull(jobDir.resolve("vulnerabilities.md"), vulnerabilitiesMd);
        writeIfNotNull(jobDir.resolve("report.md"), reportMd);

        log.info("Wrote artifacts for job {} to {}", jobId, jobDir);
    }

    /**
     * Writes round JSON log.
     */
    public void writeRoundLog(String jobId, String projectName, int roundNum, String json) throws IOException {
        Path runsDir = Paths.get(basePath, projectName, jobId, "runs");
        Files.createDirectories(runsDir);
        Files.writeString(runsDir.resolve("round-" + roundNum + ".json"), json);
    }

    /**
     * Reads requirements.md for a job.
     */
    public String readRequirements(String jobId, String projectName) throws IOException {
        Path file = Paths.get(basePath, projectName, jobId, "requirements.md");
        return Files.exists(file) ? Files.readString(file) : null;
    }

    /**
     * Reads plan.md for a job.
     */
    public String readPlan(String jobId, String projectName) throws IOException {
        Path file = Paths.get(basePath, projectName, jobId, "plan.md");
        return Files.exists(file) ? Files.readString(file) : null;
    }

    /**
     * Gets job by ID.
     */
    public Optional<JobEntity> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * Gets job by project name (latest).
     */
    public Optional<JobEntity> getLatestJob(String projectName) {
        List<JobEntity> jobs = jobRepository.findByProjectNameOrderByCreatedAtDesc(projectName);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    /**
     * Gets all rounds for a job.
     */
    public List<RoundEntity> getRounds(String jobId) {
        return roundRepository.findByJobIdOrderByRoundNumAsc(jobId);
    }

    /**
     * Gets findings for a round.
     */
    public List<FindingEntity> getFindings(String roundId) {
        return findingRepository.findByRoundId(roundId);
    }

    /**
     * Gets open findings for a round.
     */
    public List<FindingEntity> getOpenFindings(String roundId) {
        return findingRepository.findByRoundIdAndStatus(roundId, "OPEN");
    }

    /**
     * Gets cost summary for a job.
     */
    public BigDecimal getTotalCost(String jobId) {
        return costRepository.getTotalCostByJobId(jobId);
    }

    /**
     * Gets all costs for a job.
     */
    public List<CostEntity> getCosts(String jobId) {
        return costRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    private void writeIfNotNull(Path path, String content) throws IOException {
        if (content != null && !content.isBlank()) {
            Files.writeString(path, content);
        }
    }
}