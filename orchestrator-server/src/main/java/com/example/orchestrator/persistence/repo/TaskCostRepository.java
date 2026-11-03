package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.TaskCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCostRepository extends JpaRepository<TaskCostEntity, String> {

    List<TaskCostEntity> findByTaskIdOrderByStartTimeDesc(String taskId);

    List<TaskCostEntity> findByModelOrderByStartTimeDesc(String model);

    List<TaskCostEntity> findByProviderOrderByStartTimeDesc(String provider);

    @Query("SELECT t FROM TaskCostEntity t WHERE t.startTime >= :since ORDER BY t.startTime DESC")
    List<TaskCostEntity> findByStartTimeAfter(Instant since);

    Optional<TaskCostEntity> findFirstByTaskIdOrderByLastUpdateDesc(String taskId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskCostEntity t WHERE t.startTime < :before")
    void deleteByStartTimeBefore(Instant before);
}