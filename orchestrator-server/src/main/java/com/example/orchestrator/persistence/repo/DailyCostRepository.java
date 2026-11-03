package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.DailyCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyCostRepository extends JpaRepository<DailyCostEntity, java.time.LocalDate> {

    Optional<DailyCostEntity> findByDate(java.time.LocalDate date);

    @Query("SELECT d FROM DailyCostEntity d WHERE d.date >= :since ORDER BY d.date DESC")
    List<DailyCostEntity> findByDateAfter(java.time.LocalDate since);

    @Query("SELECT d FROM DailyCostEntity d ORDER BY d.date DESC")
    List<DailyCostEntity> findAllOrderByDateDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyCostEntity d WHERE d.date < :before")
    void deleteByDateBefore(LocalDate before);
}