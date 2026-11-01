package com.example.orchestrator.store;

import com.example.orchestrator.model.Finding;
import com.example.orchestrator.model.FindingStatus;
import com.example.orchestrator.model.Job;
import com.example.orchestrator.model.JobState;
import com.example.orchestrator.model.RoundReport;
import com.example.orchestrator.model.Severity;
import com.example.orchestrator.store.entity.*;
import com.example.orchestrator.store.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = {com.example.orchestrator.store.StoreTestConfig.class})
@ActiveProfiles("test")
@Transactional
class ArtifactStoreTest {

    @Autowired
    private ArtifactStore artifactStore;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RoundRepository roundRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Autowired
    private CostRepository costRepository;

    @TempDir
    Path tempDir;

    @Test
    void createAndRetrieveJob() {
        Job job = new Job(
            "job-123",
            "test-project",
            JobState.CREATED,
            1,
            BigDecimal.valueOf(10.0),
            BigDecimal.ZERO,
            Instant.now(),
            null,
            null
        );

        JobEntity created = artifactStore.createJob(job);
        assertThat(created.getId()).isEqualTo("job-123");
        assertThat(created.getProjectName()).isEqualTo("test-project");
        assertThat(created.getStatus()).isEqualTo("CREATED");

        Optional<JobEntity> found = artifactStore.getJob("job-123");
        assertThat(found).isPresent();
        assertThat(found.get().getProjectName()).isEqualTo("test-project");
    }

    @Test
    void updateJobStatus() {
        Job job = new Job("job-456", "test-project", JobState.CREATED, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), null, null);
        artifactStore.createJob(job);

        artifactStore.updateJobStatus("job-456", JobState.BUILDING);
        Optional<JobEntity> running = artifactStore.getJob("job-456");
        assertThat(running).isPresent();
        assertThat(running.get().getStatus()).isEqualTo("BUILDING");
        assertThat(running.get().getStartedAt()).isNotNull();

        artifactStore.updateJobStatus("job-456", JobState.COMPLETED);
        Optional<JobEntity> completed = artifactStore.getJob("job-456");
        assertThat(completed).isPresent();
        assertThat(completed.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.get().getFinishedAt()).isNotNull();
    }

    @Test
    void saveAndRetrieveRound() {
        Job job = new Job("job-789", "test-project", JobState.BUILDING, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), null, null);
        artifactStore.createJob(job);

        RoundReport round = new RoundReport(
            "round-1",
            "job-789",
            1,
            true,
            5,
            0,
            BigDecimal.valueOf(85.5),
            List.of(),
            List.of(),
            BigDecimal.valueOf(0.05),
            30,
            Instant.now().minusSeconds(30),
            Instant.now()
        );

        RoundEntity saved = artifactStore.saveRound(round, "job-789");
        assertThat(saved.getRoundNum()).isEqualTo(1);
        assertThat(saved.getBuildOk()).isTrue();
        assertThat(saved.getTestsPassed()).isEqualTo(5);
        assertThat(saved.getCoverage()).isEqualByComparingTo(BigDecimal.valueOf(85.5));

        List<RoundEntity> rounds = artifactStore.getRounds("job-789");
        assertThat(rounds).hasSize(1);
    }

    @Test
    void saveAndRetrieveFinding() {
        Job job = new Job("job-find", "test-project", JobState.VERIFYING, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), null, null);
        artifactStore.createJob(job);

        RoundReport round = new RoundReport(
            "round-f1",
            "job-find",
            1,
            true,
            0,
            1,
            BigDecimal.ZERO,
            List.of(),
            List.of(),
            BigDecimal.ZERO,
            10,
            Instant.now().minusSeconds(10),
            Instant.now()
        );
        artifactStore.saveRound(round, "job-find");

        // Finding(fingerprint, tool, ruleId, severity, file, line, message, firstSeenRound, fixedInRound, status, createdAt)
        Finding finding = new Finding(
            "fp-abc123",
            "spotbugs",
            "NP_NULL_ON_SOME_PATH",
            Severity.HIGH,
            "src/main/java/Foo.java",
            42,
            "Null pointer dereference",
            1,
            null,
            FindingStatus.OPEN,
            Instant.now()
        );

        FindingEntity saved = artifactStore.saveFinding(finding, "round-f1");
        assertThat(saved.getFingerprint()).isEqualTo("fp-abc123");
        assertThat(saved.getTool()).isEqualTo("spotbugs");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getStatus()).isEqualTo("OPEN");

        List<FindingEntity> findings = artifactStore.getFindings("round-f1");
        assertThat(findings).hasSize(1);

        List<FindingEntity> openFindings = artifactStore.getOpenFindings("round-f1");
        assertThat(openFindings).hasSize(1);
    }

    @Test
    void saveAndRetrieveCost() {
        Job job = new Job("job-cost", "test-project", JobState.FIXING, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), null, null);
        artifactStore.createJob(job);

        CostEntity cost = artifactStore.saveCost(
            "job-cost", "round-1", "task-1",
            "coder", "nvidia", "nemotron-3-ultra",
            1000, 500,
            BigDecimal.valueOf(0.0015)
        );

        assertThat(cost.getProfile()).isEqualTo("coder");
        assertThat(cost.getProvider()).isEqualTo("nvidia");
        assertThat(cost.getCostUsd()).isEqualByComparingTo(BigDecimal.valueOf(0.0015));

        BigDecimal total = artifactStore.getTotalCost("job-cost");
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(0.0015));

        List<CostEntity> costs = artifactStore.getCosts("job-cost");
        assertThat(costs).hasSize(1);
    }

    @Test
    void writeAndReadArtifacts(@TempDir Path tempDir) throws IOException {
        // Use temp dir for this test
        System.setProperty("orchestrator.store.base-path", tempDir.toString());
        
        Job job = new Job("job-art", "test-project", JobState.COMPLETED, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), Instant.now(), null);
        artifactStore.createJob(job);

        artifactStore.writeArtifacts(
            "job-art", "test-project",
            "# Requirements\n\nTest requirements",
            "# Plan\n\nTest plan",
            "# Fixes\n\nTest fixes",
            "# Vulnerabilities\n\nTest vulns",
            "# Report\n\nTest report"
        );

        String requirements = artifactStore.readRequirements("job-art", "test-project");
        assertThat(requirements).contains("Test requirements");

        String plan = artifactStore.readPlan("job-art", "test-project");
        assertThat(plan).contains("Test plan");
    }

    @Test
    void getLatestJobByProjectName() {
        artifactStore.createJob(new Job("job-old", "my-project", JobState.COMPLETED, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, 
            Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600), null));
        artifactStore.createJob(new Job("job-new", "my-project", JobState.COMPLETED, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, 
            Instant.now(), Instant.now(), null));

        Optional<JobEntity> latest = artifactStore.getLatestJob("my-project");
        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo("job-new");
    }

    @Test
    void findingCountBySeverity() {
        Job job = new Job("job-sev", "test-project", JobState.VERIFYING, 1, 
            BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now(), null, null);
        artifactStore.createJob(job);

        RoundReport round = new RoundReport("round-s", "job-sev", 1, true, 0, 0, BigDecimal.ZERO, List.of(), List.of(), BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        artifactStore.saveRound(round, "job-sev");

        FindingEntity high = new FindingEntity("fp1", "round-s", "fp1", "spotbugs", "RULE1", "HIGH", "A.java", 1, "msg", "", "OPEN", 1, null, Instant.now());
        FindingEntity med = new FindingEntity("fp2", "round-s", "fp2", "checkstyle", "RULE2", "MEDIUM", "B.java", 2, "msg", "", "OPEN", 1, null, Instant.now());
        findingRepository.save(high);
        findingRepository.save(med);

        long highCount = findingRepository.countByRoundIdAndSeverity("round-s", "HIGH");
        long medCount = findingRepository.countByRoundIdAndSeverity("round-s", "MEDIUM");

        assertThat(highCount).isEqualTo(1);
        assertThat(medCount).isEqualTo(1);
    }
}