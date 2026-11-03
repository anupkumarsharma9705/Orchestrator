package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.CostAggregateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CostAggregateRepository extends JpaRepository<CostAggregateEntity, String> {

    Optional<CostAggregateEntity> findById(String id);
}