package com.example.orchestrator.project;

import com.example.orchestrator.persistence.entity.ProjectEntity;
import com.example.orchestrator.persistence.repo.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages project registration and persistence with backup/restore support.
 * H-13: Added backup/restore functionality for disaster recovery.
 */
@Service
public class ProjectManager {

    private static final Logger log = LoggerFactory.getLogger(ProjectManager.class);

    private final ProjectRepository projectRepository;

    // Backup directory
    private final Path backupDir;

    public ProjectManager(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.backupDir = Path.of(System.getProperty("user.home"), ".orchestrator", "backups", "projects");
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            log.warn("Could not create backup directory: {}", e.getMessage());
        }
    }

    public Project register(String id, String name, Path rootPath, String buildTool, String testCommand, Map<String, String> environment) {
        Path memoryPath = rootPath.resolve(".ai");
        Project project = new Project(
                id,
                name,
                rootPath.toAbsolutePath().normalize(),
                memoryPath.toAbsolutePath().normalize(),
                buildTool,
                testCommand,
                environment,
                Instant.now(),
                Instant.now()
        );

        ProjectEntity entity = toEntity(project);
        projectRepository.save(entity);

        return project;
    }

    public Optional<Project> get(String id) {
        return projectRepository.findById(id)
                .map(entity -> {
                    Project project = toDomain(entity);
                    // Update last accessed time
                    entity.setLastAccessedAt(Instant.now());
                    projectRepository.save(entity);
                    return project.withLastAccessedAt(Instant.now());
                });
    }

    public void unregister(String id) {
        projectRepository.deleteById(id);
    }

    public List<Project> listAll() {
        return projectRepository.findAllByOrderByLastAccessedAtDesc().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public boolean exists(String id) {
        return projectRepository.existsById(id);
    }

    // ===== Backup/Restore (H-13) =====

    /**
     * Creates a backup of all projects to a JSON file.
     * @return Path to the backup file
     */
    public Path createBackup() {
        String timestamp = java.time.format.DateTimeFormatter.ISO_INSTANT
            .format(Instant.now()).replace(":", "-").replace(".", "-");
        Path backupPath = backupDir.resolve("projects-backup-" + timestamp + ".json");
        
        try {
            List<Project> projects = listAll();
            var projectList = projects.stream().map(p -> Map.of(
                "id", p.id(),
                "name", p.name(),
                "rootPath", p.rootPath().toString(),
                "memoryPath", p.memoryPath().toString(),
                "buildTool", p.buildTool(),
                "testCommand", p.testCommand(),
                "environment", p.environment(),
                "registeredAt", p.registeredAt().toString(),
                "lastAccessedAt", p.lastAccessedAt().toString()
            )).toList();
            
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(projectList);
            Files.writeString(backupPath, json);
            
            log.info("ProjectManager backup created at: {}", backupPath);
            return backupPath;
            
        } catch (IOException e) {
            log.error("Failed to create backup: {}", e.getMessage(), e);
            throw new RuntimeException("Backup failed", e);
        }
    }

    /**
     * Restores projects from a backup file.
     * @param backupPath Path to the backup file
     */
    public void restoreFromBackup(Path backupPath) {
        try {
            String json = Files.readString(backupPath);
            var projectList = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            
            for (var item : projectList) {
                Project project = new Project(
                    (String) item.get("id"),
                    (String) item.get("name"),
                    Path.of((String) item.get("rootPath")),
                    Path.of((String) item.get("memoryPath")),
                    (String) item.get("buildTool"),
                    (String) item.get("testCommand"),
                    (Map<String, String>) item.get("environment"),
                    Instant.parse((String) item.get("registeredAt")),
                    Instant.parse((String) item.get("lastAccessedAt"))
                );
                
                ProjectEntity entity = toEntity(project);
                projectRepository.save(entity);
            }
            
            log.info("ProjectManager restored from backup: {}", backupPath);
            
        } catch (IOException e) {
            log.error("Failed to restore from backup: {}", e.getMessage(), e);
            throw new RuntimeException("Restore failed", e);
        }
    }

    /**
     * Lists available backups.
     * @return List of backup files sorted by date (newest first)
     */
    public List<Path> listBackups() {
        try {
            if (!Files.exists(backupDir)) return List.of();
            return Files.list(backupDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            log.warn("Failed to list backups: {}", e.getMessage());
            return List.of();
        }
    }

    private ProjectEntity toEntity(Project project) {
        String environmentJson = "{}";
        try {
            environmentJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(project.environment());
        } catch (Exception ignored) {
        }

        return new ProjectEntity(
                project.id(),
                project.name(),
                project.rootPath().toString(),
                project.memoryPath().toString(),
                project.buildTool(),
                project.testCommand(),
                environmentJson,
                project.registeredAt(),
                project.lastAccessedAt()
        );
    }

    private Project toDomain(ProjectEntity entity) {
        Map<String, String> environment = Map.of();
        try {
            environment = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(entity.getEnvironmentJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception ignored) {
        }

        return new Project(
                entity.getId(),
                entity.getName(),
                Path.of(entity.getRootPath()),
                Path.of(entity.getMemoryPath()),
                entity.getBuildTool(),
                entity.getTestCommand(),
                environment,
                entity.getRegisteredAt(),
                entity.getLastAccessedAt()
        );
    }
}