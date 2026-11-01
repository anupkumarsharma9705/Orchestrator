package com.example.orchestrator.store.repository;

import com.example.orchestrator.store.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for JobEntity.
 */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, String> {

    Optional<JobEntity> findByProjectName(String projectName);

    List<JobEntity> findByStatus(String status);

    @Query("SELECT j FROM JobEntity j WHERE j.projectName = :projectName ORDER BY j.createdAt DESC")
    List<JobEntity> findByProjectNameOrderByCreatedAtDesc(@Param("projectName") String projectName);

    @Query("SELECT j FROM JobEntity j WHERE j.status IN ('PENDING', 'RUNNING')")
    List<JobEntity> findActiveJobs();
}