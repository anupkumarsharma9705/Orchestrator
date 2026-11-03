package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.RepairSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepairSessionRepository extends JpaRepository<RepairSessionEntity, String> {

    List<RepairSessionEntity> findByTaskIdOrderByStartedAtDesc(String taskId);

    List<RepairSessionEntity> findByProjectIdOrderByStartedAtDesc(String projectId);

    Optional<RepairSessionEntity> findFirstByTaskIdOrderByStartedAtDesc(String taskId);
}