package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.ExecutionStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionStateRepository extends JpaRepository<ExecutionStateEntity, String> {

    List<ExecutionStateEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<ExecutionStateEntity> findByStatusOrderByCreatedAtDesc(
            com.example.orchestrator.execution.ExecutionStatus status);

    @Query("SELECT e FROM ExecutionStateEntity e WHERE e.projectId = :projectId AND e.status IN :statuses ORDER BY e.createdAt DESC")
    List<ExecutionStateEntity> findByProjectIdAndStatusIn(
            String projectId, List<com.example.orchestrator.execution.ExecutionStatus> statuses);

    Optional<ExecutionStateEntity> findFirstByProjectIdOrderByCreatedAtDesc(String projectId);

    long countByProjectIdAndStatus(String projectId, com.example.orchestrator.execution.ExecutionStatus status);
}