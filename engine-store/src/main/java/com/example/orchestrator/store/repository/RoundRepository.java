package com.example.orchestrator.store.repository;

import com.example.orchestrator.store.entity.RoundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RoundEntity.
 */
@Repository
public interface RoundRepository extends JpaRepository<RoundEntity, String> {

    List<RoundEntity> findByJobIdOrderByRoundNumAsc(String jobId);

    @Query("SELECT r FROM RoundEntity r WHERE r.jobId = :jobId AND r.roundNum = :roundNum")
    Optional<RoundEntity> findByJobIdAndRoundNum(@Param("jobId") String jobId, @Param("roundNum") Integer roundNum);

    List<RoundEntity> findByJobId(String jobId);
}