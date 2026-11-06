package com.example.orchestrator.approval;

import com.example.orchestrator.persistence.entity.ApprovalEntity;
import com.example.orchestrator.persistence.entity.ApprovalAuditEntity;
import com.example.orchestrator.persistence.repo.ApprovalRepository;
import com.example.orchestrator.persistence.repo.ApprovalAuditRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalService {

    @Value("${orchestrator.approval.auto-approve:false}")
    private boolean autoApprove;

    private final ApprovalRepository approvalRepository;
    private final ApprovalAuditRepository approvalAuditRepository;

    // Keep in-memory locks for synchronization (these don't need persistence)
    private final Map<String, Object> waitingLocks = new ConcurrentHashMap<>();

    public ApprovalService(ApprovalRepository approvalRepository, ApprovalAuditRepository approvalAuditRepository) {
        this.approvalRepository = approvalRepository;
        this.approvalAuditRepository = approvalAuditRepository;
    }

    @Transactional
    public void requestApproval(String taskId) {
        // Clear any stale approval for this task
        approvalRepository.findByTaskId(taskId).ifPresent(existing -> {
            // Archive the old approval if it was pending
            if (existing.getStatus() == ApprovalDecision.Status.PENDING) {
                existing.setStatus(ApprovalDecision.Status.CANCELLED);
                existing.setDecidedAt(Instant.now());
                approvalRepository.save(existing);
            }
        });

        if (autoApprove) {
            // Auto-approve immediately
            ApprovalDecision decision = ApprovalDecision.approved(taskId, "auto", "Auto-approved by configuration");
            saveApproval(taskId, decision);
            return;
        }

        waitingLocks.put(taskId, new Object());
    }

    public ApprovalDecision waitForApproval(String taskId, long timeoutMs) throws InterruptedException {
        Object lock = waitingLocks.get(taskId);
        if (lock == null) {
            // Check if already approved/rejected
            return approvalRepository.findByTaskId(taskId)
                    .map(this::toDecision)
                    .orElse(null);
        }

        try {
            synchronized (lock) {
                long start = System.currentTimeMillis();
                while (true) {
                    Optional<ApprovalEntity> approval = approvalRepository.findByTaskId(taskId);
                    if (approval.isPresent() && approval.get().getStatus() != ApprovalDecision.Status.PENDING) {
                        return toDecision(approval.get());
                    }

                    long remaining = timeoutMs - (System.currentTimeMillis() - start);
                    if (remaining <= 0) {
                        // Timeout - clean up waiting lock
                        waitingLocks.remove(taskId);
                        return null; // Timeout
                    }
                    lock.wait(remaining);
                }
            }
        } finally {
            // Clean up the lock on timeout or success
            waitingLocks.remove(taskId);
        }
    }

    @Transactional
    public void submitApproval(ApprovalDecision decision) {
        String taskId = decision.taskId();
        if (!waitingLocks.containsKey(taskId)) {
            throw new IllegalStateException("No pending approval request for task: " + taskId);
        }

        saveApproval(taskId, decision);

        Object lock = waitingLocks.remove(taskId);
        if (lock != null) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Transactional
    public void submitRejection(String taskId, String approver, String comment) {
        if (!waitingLocks.containsKey(taskId)) {
            throw new IllegalStateException("No pending approval request for task: " + taskId);
        }

        ApprovalDecision decision = ApprovalDecision.rejected(taskId, approver, comment);
        saveApproval(taskId, decision);

        Object lock = waitingLocks.remove(taskId);
        if (lock != null) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    private void saveApproval(String taskId, ApprovalDecision decision) {
        String id = UUID.randomUUID().toString();
        ApprovalEntity entity = new ApprovalEntity(
                id,
                taskId,
                decision.decision() == ApprovalDecision.Decision.APPROVED ? ApprovalDecision.Status.APPROVED : ApprovalDecision.Status.REJECTED,
                decision.approver(),
                decision.comment(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        entity.setStatus(decision.decision() == ApprovalDecision.Decision.APPROVED ? ApprovalDecision.Status.APPROVED : ApprovalDecision.Status.REJECTED);
        entity.setApprover(decision.approver());
        entity.setComment(decision.comment());
        entity.setDecidedAt(Instant.now());

        approvalRepository.save(entity);

        // Also save audit record
        ApprovalAuditEntity audit = new ApprovalAuditEntity(
                UUID.randomUUID().toString(),
                id,
                decision.decision() == ApprovalDecision.Decision.APPROVED ? ApprovalDecision.Action.APPROVED : ApprovalDecision.Action.REJECTED,
                decision.approver(),
                decision.comment(),
                Instant.now()
        );
        approvalAuditRepository.save(audit);

        // Update the entity with the generated ID for audit
        entity.setId(id);
        approvalRepository.save(entity);
    }

    public Optional<ApprovalDecision> getApproval(String taskId) {
        return approvalRepository.findByTaskId(taskId)
                .map(this::toDecision);
    }

    public boolean isPending(String taskId) {
        Optional<ApprovalEntity> entity = approvalRepository.findByTaskId(taskId);
        return entity.isPresent() && entity.get().getStatus() == ApprovalDecision.Status.PENDING;
    }

    /**
     * A task is awaiting a decision when the execution thread is blocked on its
     * approval lock. Unlike {@link #isPending(String)} this also covers the
     * window before the first approval row is persisted.
     */
    public boolean isAwaitingDecision(String taskId) {
        return taskId != null && waitingLocks.containsKey(taskId);
    }

    private ApprovalDecision toDecision(ApprovalEntity entity) {
        return new ApprovalDecision(
                entity.getTaskId(),
                entity.getStatus() == ApprovalDecision.Status.APPROVED ? ApprovalDecision.Decision.APPROVED : ApprovalDecision.Decision.REJECTED,
                entity.getApprover(),
                entity.getComment(),
                entity.getDecidedAt() != null ? entity.getDecidedAt() : entity.getCreatedAt()
        );
    }

    @Transactional
    public void cancelApproval(String taskId) {
        Optional<ApprovalEntity> existing = approvalRepository.findByTaskId(taskId);
        if (existing.isPresent() && existing.get().getStatus() == ApprovalDecision.Status.PENDING) {
            ApprovalEntity entity = existing.get();
            entity.setStatus(ApprovalDecision.Status.CANCELLED);
            entity.setDecidedAt(Instant.now());
            approvalRepository.save(entity);

            ApprovalAuditEntity audit = new ApprovalAuditEntity(
                    UUID.randomUUID().toString(),
                    entity.getId(),
                    ApprovalDecision.Action.CANCELLED,
                    "system",
                    "Cancelled by system",
                    Instant.now()
            );
            approvalAuditRepository.save(audit);
        }
    }
}