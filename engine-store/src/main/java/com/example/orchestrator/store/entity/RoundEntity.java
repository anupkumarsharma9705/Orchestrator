package com.example.orchestrator.store.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Round entity for tracking verification rounds within a job.
 */
@Entity
@Table(name = "rounds")
public class RoundEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "round_num", nullable = false)
    private Integer roundNum;

    @Column(name = "build_ok", nullable = false)
    private Boolean buildOk = false;

    @Column(name = "tests_passed", nullable = false)
    private Integer testsPassed = 0;

    @Column(name = "tests_failed", nullable = false)
    private Integer testsFailed = 0;

    @Column(name = "coverage", precision = 5, scale = 2)
    private BigDecimal coverage = BigDecimal.ZERO;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "duration_sec")
    private Integer durationSec = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public RoundEntity() {}

    public RoundEntity(String id, String jobId, Integer roundNum, Boolean buildOk,
                       Integer testsPassed, Integer testsFailed, BigDecimal coverage,
                       BigDecimal costUsd, Integer durationSec, Instant startedAt,
                       Instant finishedAt, String metadataJson) {
        this.id = id;
        this.jobId = jobId;
        this.roundNum = roundNum;
        this.buildOk = buildOk;
        this.testsPassed = testsPassed;
        this.testsFailed = testsFailed;
        this.coverage = coverage;
        this.costUsd = costUsd;
        this.durationSec = durationSec;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.metadataJson = metadataJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public Integer getRoundNum() { return roundNum; }
    public void setRoundNum(Integer roundNum) { this.roundNum = roundNum; }

    public Boolean getBuildOk() { return buildOk; }
    public void setBuildOk(Boolean buildOk) { this.buildOk = buildOk; }

    public Integer getTestsPassed() { return testsPassed; }
    public void setTestsPassed(Integer testsPassed) { this.testsPassed = testsPassed; }

    public Integer getTestsFailed() { return testsFailed; }
    public void setTestsFailed(Integer testsFailed) { this.testsFailed = testsFailed; }

    public BigDecimal getCoverage() { return coverage; }
    public void setCoverage(BigDecimal coverage) { this.coverage = coverage; }

    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }

    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoundEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}