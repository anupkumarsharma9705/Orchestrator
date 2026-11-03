package com.example.orchestrator.approval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApprovalDecision(
    @JsonProperty("taskId") String taskId,
    @JsonProperty("decision") Decision decision,
    @JsonProperty("approver") String approver,
    @JsonProperty("comment") String comment,
    @JsonProperty("timestamp") Instant timestamp
) {
    public enum Decision { APPROVED, REJECTED }

    public enum Status { PENDING, APPROVED, REJECTED, TIMEOUT, CANCELLED }

    public enum Action { REQUESTED, APPROVED, REJECTED, TIMEOUT, CANCELLED }

    public static ApprovalDecision approved(String taskId, String approver, String comment) {
        return new ApprovalDecision(taskId, Decision.APPROVED, approver, comment, Instant.now());
    }

    public static ApprovalDecision rejected(String taskId, String approver, String comment) {
        return new ApprovalDecision(taskId, Decision.REJECTED, approver, comment, Instant.now());
    }
}