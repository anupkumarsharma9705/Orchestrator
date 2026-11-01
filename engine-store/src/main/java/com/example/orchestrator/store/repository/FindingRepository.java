package com.example.orchestrator.store.repository;

import com.example.orchestrator.store.entity.FindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FindingEntity.
 */
@Repository
public interface FindingRepository extends JpaRepository<FindingEntity, String> {

    List<FindingEntity> findByRoundId(String roundId);

    List<FindingEntity> findByFingerprint(String fingerprint);

    List<FindingEntity> findByStatus(String status);

    @Query("SELECT f FROM FindingEntity f WHERE f.roundId = :roundId AND f.status = :status")
    List<FindingEntity> findByRoundIdAndStatus(@Param("roundId") String roundId, @Param("status") String status);

    @Query("SELECT f FROM FindingEntity f WHERE f.fingerprint = :fingerprint ORDER BY f.createdAt DESC")
    Optional<FindingEntity> findLatestByFingerprint(@Param("fingerprint") String fingerprint);

    long countByRoundIdAndSeverity(String roundId, String severity);

    long countByRoundIdAndStatus(String roundId, String status);
}