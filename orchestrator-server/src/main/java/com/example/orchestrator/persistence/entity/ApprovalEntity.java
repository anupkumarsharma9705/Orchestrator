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
@Table(name = "approvals")
public class ApprovalEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64, unique = true)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApprovalDecision.Status status;

    @Column(name = "approver", length = 128)
    private String approver;

    @Column(name = "comment", length = 4000)
    private String comment;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ApprovalEntity() {
    }

    public ApprovalEntity(String id, String taskId, ApprovalDecision.Status status,
                          String approver, String comment,
                          Instant requestedAt, Instant decidedAt, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.status = status;
        this.approver = approver;
        this.comment = comment;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public ApprovalDecision.Status getStatus() { return status; }
    public void setStatus(ApprovalDecision.Status status) { this.status = status; }

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}