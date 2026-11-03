package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.ProjectMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectMemoryRepository extends JpaRepository<ProjectMemoryEntity, Long> {

    List<ProjectMemoryEntity> findByProjectIdOrderByConfidenceDescUsageCountDesc(String projectId);

    List<ProjectMemoryEntity> findByProjectIdAndType(String projectId, ProjectMemoryEntity.MemoryType type);

    @Query("SELECT p FROM ProjectMemoryEntity p WHERE p.projectId = :projectId AND p.confidence >= :minConfidence ORDER BY p.confidence DESC, p.usageCount DESC")
    List<ProjectMemoryEntity> findHighConfidenceByProjectId(String projectId, Double minConfidence);

    long countByProjectId(String projectId);

    void deleteByProjectId(String projectId);
}