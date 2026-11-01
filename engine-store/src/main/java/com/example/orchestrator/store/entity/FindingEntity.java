package com.example.orchestrator.store.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Finding entity for tracking verification findings across rounds.
 */
@Entity
@Table(name = "findings")
public class FindingEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "round_id", nullable = false, length = 64)
    private String roundId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "tool", nullable = false, length = 100)
    private String tool;

    @Column(name = "rule_id", nullable = false, length = 255)
    private String ruleId;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber = 0;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "code_snippet", columnDefinition = "TEXT")
    private String codeSnippet;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "first_seen_round", nullable = false)
    private Integer firstSeenRound;

    @Column(name = "fixed_in_round")
    private Integer fixedInRound;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FindingEntity() {}

    public FindingEntity(String id, String roundId, String fingerprint, String tool,
                         String ruleId, String severity, String filePath, Integer lineNumber,
                         String message, String codeSnippet, String status,
                         Integer firstSeenRound, Integer fixedInRound, Instant createdAt) {
        this.id = id;
        this.roundId = roundId;
        this.fingerprint = fingerprint;
        this.tool = tool;
        this.ruleId = ruleId;
        this.severity = severity;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.message = message;
        this.codeSnippet = codeSnippet;
        this.status = status;
        this.firstSeenRound = firstSeenRound;
        this.fixedInRound = fixedInRound;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCodeSnippet() { return codeSnippet; }
    public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getFirstSeenRound() { return firstSeenRound; }
    public void setFirstSeenRound(Integer firstSeenRound) { this.firstSeenRound = firstSeenRound; }

    public Integer getFixedInRound() { return fixedInRound; }
    public void setFixedInRound(Integer fixedInRound) { this.fixedInRound = fixedInRound; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FindingEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}