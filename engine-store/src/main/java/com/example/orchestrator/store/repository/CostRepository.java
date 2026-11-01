package com.example.orchestrator.store.repository;

import com.example.orchestrator.store.entity.CostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for CostEntity.
 */
@Repository
public interface CostRepository extends JpaRepository<CostEntity, String> {

    List<CostEntity> findByJobId(String jobId);

    List<CostEntity> findByRoundId(String roundId);

    @Query("SELECT SUM(c.costUsd) FROM CostEntity c WHERE c.jobId = :jobId")
    BigDecimal getTotalCostByJobId(@Param("jobId") String jobId);

    @Query("SELECT SUM(c.costUsd) FROM CostEntity c WHERE c.roundId = :roundId")
    BigDecimal getTotalCostByRoundId(@Param("roundId") String roundId);

    @Query("SELECT c FROM CostEntity c WHERE c.jobId = :jobId ORDER BY c.createdAt ASC")
    List<CostEntity> findByJobIdOrderByCreatedAtAsc(@Param("jobId") String jobId);
}