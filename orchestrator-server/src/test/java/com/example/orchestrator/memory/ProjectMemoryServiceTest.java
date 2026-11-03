package com.example.orchestrator.memory;

import com.example.orchestrator.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldInitializeMemoryFiles() throws IOException {
        Project project = new Project(
            "test-project",
            "Test Project",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        ProjectMemoryService service = new ProjectMemoryService();
        service.initializeMemory(project);
        
        // Check all memory files exist
        for (String fileName : ProjectMemoryService.MEMORY_FILES) {
            Path file = project.memoryPath().resolve(fileName);
            assertTrue(Files.exists(file), "Memory file should exist: " + fileName);
            String content = Files.readString(file);
            assertFalse(content.isBlank(), "Memory file should have content: " + fileName);
        }
    }

    @Test
    void shouldLoadMemoryBundle() throws IOException {
        Project project = new Project(
            "test-project",
            "Test Project",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        // Initialize first
        ProjectMemoryService service = new ProjectMemoryService();
        service.initializeMemory(project);
        
        // Modify one file
        Files.writeString(project.memoryPath().resolve("CURRENT_STATE.md"), "# Current State\n\n- Working on feature X");
        
        MemoryBundle bundle = service.loadMemory(project);
        
        assertNotNull(bundle);
        assertEquals("test-project", bundle.projectId());
        assertTrue(bundle.allMemories().containsKey("CURRENT_STATE.md"));
        assertTrue(bundle.priorityMemories().containsKey("CURRENT_STATE.md"));
        assertTrue(bundle.priorityMemories().get("CURRENT_STATE.md").contains("feature X"));
    }

    @Test
    void shouldWriteMemoryFile() throws IOException {
        Project project = new Project(
            "test-project",
            "Test Project",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        ProjectMemoryService service = new ProjectMemoryService();
        service.initializeMemory(project);
        
        String newContent = "# Updated TODO\n\n- [ ] New task";
        service.writeMemoryFile(project, "TODO.md", newContent);
        
        String content = Files.readString(project.memoryPath().resolve("TODO.md"));
        assertEquals(newContent, content);
    }

    @Test
    void shouldRejectInvalidMemoryFile() {
        Project project = new Project(
            "test-project",
            "Test Project",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        ProjectMemoryService service = new ProjectMemoryService();
        
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                service.writeMemoryFile(project, "INVALID.md", "content");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}