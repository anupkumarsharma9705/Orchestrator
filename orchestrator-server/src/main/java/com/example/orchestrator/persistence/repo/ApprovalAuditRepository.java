package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.ApprovalAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ApprovalAuditRepository extends JpaRepository<ApprovalAuditEntity, String> {

    List<ApprovalAuditEntity> findByApprovalIdOrderByTimestampAsc(String approvalId);

    @Query("SELECT a FROM ApprovalAuditEntity a WHERE a.timestamp >= :since ORDER BY a.timestamp ASC")
    List<ApprovalAuditEntity> findByTimestampAfter(Instant since);
}