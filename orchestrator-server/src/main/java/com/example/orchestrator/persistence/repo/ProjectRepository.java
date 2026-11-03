package com.example.orchestrator.persistence.repo;

import com.example.orchestrator.persistence.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    List<ProjectEntity> findAllByOrderByLastAccessedAtDesc();

    Optional<ProjectEntity> findByName(String name);

    boolean existsById(String id);
}