package com.example.orchestrator.project;

import com.example.orchestrator.persistence.entity.ProjectEntity;
import com.example.orchestrator.persistence.repo.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectManagerTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectManager manager;

    @Test
    void shouldRegisterAndRetrieveProject() {
        Path rootPath = Path.of("/tmp/project1");
        Project project = new Project("proj-1", "Project One", rootPath, rootPath.resolve(".ai"),
                "maven", "test", Map.of(), Instant.now(), Instant.now());

        Project registered = manager.register("proj-1", "Project One", rootPath, "maven", "test", Map.of());

        assertNotNull(registered);
        assertEquals("proj-1", registered.id());
        assertEquals("Project One", registered.name());
        assertEquals(rootPath.toAbsolutePath().normalize(), registered.rootPath());
        assertEquals("maven", registered.buildTool());
        assertEquals("test", registered.testCommand());
        
        verify(projectRepository).save(any(ProjectEntity.class));
    }

    @Test
    void shouldReturnOptionalWhenGettingProject() {
        Path rootPath = Path.of("/tmp/project1");
        Project project = new Project("proj-1", "Project One", rootPath, rootPath.resolve(".ai"),
                "maven", "test", Map.of(), Instant.now(), Instant.now());

        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(toEntity(project)));
        when(projectRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Project> found = manager.get("proj-1");
        assertTrue(found.isPresent());
        assertEquals("proj-1", found.get().id());

        Optional<Project> notFound = manager.get("non-existent");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void shouldListAllProjects() {
        Path rootPath1 = Path.of("/tmp/p1");
        Path rootPath2 = Path.of("/tmp/p2");
        Project project1 = new Project("proj-1", "Project One", rootPath1, rootPath1.resolve(".ai"),
                "maven", "test", Map.of(), Instant.now(), Instant.now());
        Project project2 = new Project("proj-2", "Project Two", rootPath2, rootPath2.resolve(".ai"),
                "gradle", "build", Map.of(), Instant.now(), Instant.now());

        when(projectRepository.findAllByOrderByLastAccessedAtDesc()).thenReturn(List.of(toEntity(project1), toEntity(project2)));

        List<Project> all = manager.listAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.id().equals("proj-1")));
        assertTrue(all.stream().anyMatch(p -> p.id().equals("proj-2")));
    }

@Test
    void shouldUnregisterProject() {
        when(projectRepository.existsById("proj-1")).thenReturn(true, false);
        doNothing().when(projectRepository).deleteById("proj-1");
        when(projectRepository.findById("proj-1")).thenReturn(Optional.empty());

        manager.register("proj-1", "Project One", Path.of("/tmp/p1"), "maven", "test", Map.of());
        assertTrue(manager.exists("proj-1"));

        manager.unregister("proj-1");

        verify(projectRepository).deleteById("proj-1");
        assertFalse(manager.exists("proj-1"));
        assertTrue(manager.get("proj-1").isEmpty());
    }

    @Test
    void shouldUpdateLastAccessedOnGet() {
        Path rootPath = Path.of("/tmp/project1");
        Project project = new Project("proj-1", "Project One", rootPath, rootPath.resolve(".ai"),
                "maven", "test", Map.of(), Instant.now(), Instant.now());

        ProjectEntity entity = toEntity(project);
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(entity));

        Optional<Project> found = manager.get("proj-1");
        assertTrue(found.isPresent());
        verify(projectRepository).save(any(ProjectEntity.class));
    }

    private ProjectEntity toEntity(Project project) {
        String environmentJson = "{}";
        try {
            environmentJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(project.environment());
        } catch (Exception ignored) {
        }
        return new ProjectEntity(
                project.id(), project.name(), project.rootPath().toString(), project.memoryPath().toString(),
                project.buildTool(), project.testCommand(), environmentJson,
                project.registeredAt(), project.lastAccessedAt()
        );
    }
}