package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {

    Optional<ApprovalEntity> findByTaskId(String taskId);

    List<ApprovalEntity> findByStatusOrderByRequestedAtDesc(com.example.orchestrator.approval.ApprovalDecision.Status status);

    List<ApprovalEntity> findByRequestedAtBetween(Instant start, Instant end);

    Optional<ApprovalEntity> findFirstByOrderByRequestedAtDesc();
}