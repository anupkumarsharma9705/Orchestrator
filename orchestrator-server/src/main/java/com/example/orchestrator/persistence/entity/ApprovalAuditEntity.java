package com.example.orchestrator.persistence.entity;

import com.example.orchestrator.approval.ApprovalDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "approval_audit")
public class ApprovalAuditEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "approval_id", nullable = false, length = 64)
    private String approvalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private ApprovalDecision.Action action;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Column(name = "comment", length = 4000)
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant timestamp;

    public ApprovalAuditEntity() {
    }

    public ApprovalAuditEntity(String id, String approvalId, ApprovalDecision.Action action,
                               String actor, String comment, Instant timestamp) {
        this.id = id;
        this.approvalId = approvalId;
        this.action = action;
        this.actor = actor;
        this.comment = comment;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public ApprovalDecision.Action getAction() { return action; }
    public void setAction(ApprovalDecision.Action action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}