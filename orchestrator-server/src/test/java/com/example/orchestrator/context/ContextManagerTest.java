package com.example.orchestrator.context;

import com.example.orchestrator.memory.MemoryBundle;
import com.example.orchestrator.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSelectRelevantFiles() throws IOException {
        // Create test project structure
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        Files.writeString(srcDir.resolve("Controller.java"), """
            package com.example;
            
            @RestController
            public class Controller {
                @GetMapping("/api/users")
                public List<User> getUsers() { return List.of(); }
            }
            """);
        
        Files.writeString(srcDir.resolve("Service.java"), """
            package com.example;
            
            @Service
            public class Service {
                public void process() { /* logic */ }
            }
            """);
        
        Files.writeString(tempDir.resolve("README.md"), "# Project\n\nThis is a test project.");
        
        // Create target dir (should be excluded)
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("output.txt"), "build output");
        
        // Create .git dir (should be excluded)
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("config"), "git config");
        
        Project project = new Project(
            "test-project",
            "Test Project",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            java.time.Instant.now(),
            java.time.Instant.now()
        );
        
        ContextManager contextManager = new ContextManager();
        MemoryBundle memoryBundle = new MemoryBundle("test-project", java.util.Map.of(), java.util.Map.of());
        
        ContextBundle context = contextManager.selectContext(project, "Add a new endpoint to get users", memoryBundle);
        
        assertNotNull(context);
        assertEquals("test-project", context.projectId());
        assertFalse(context.files().isEmpty());
        
        // Should include Controller.java (contains "users" and "Controller")
        assertTrue(context.files().stream().anyMatch(f -> f.path().contains("Controller.java")));
        
        // Should not include target directory
        assertFalse(context.files().stream().anyMatch(f -> f.path().startsWith("target/")));
        
        // Should not include .git directory
        assertFalse(context.files().stream().anyMatch(f -> f.path().startsWith(".git/")));
    }

    @Test
    void shouldExcludeBuildArtifacts() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        
        Files.writeString(srcDir.resolve("App.java"), "public class App {}");
        
        Path buildDir = tempDir.resolve("build");
        Files.createDirectories(buildDir);
        Files.writeString(buildDir.resolve("generated.java"), "// generated");
        
        Path nodeModules = tempDir.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Files.writeString(nodeModules.resolve("package.json"), "{}");
        
        Project project = new Project(
            "test",
            "Test",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            java.util.Map.of(),
            java.time.Instant.now(),
            java.time.Instant.now()
        );
        
        ContextManager contextManager = new ContextManager();
        MemoryBundle memoryBundle = new MemoryBundle("test", java.util.Map.of(), java.util.Map.of());
        
        ContextBundle context = contextManager.selectContext(project, "test", new MemoryBundle("test", java.util.Map.of(), java.util.Map.of()));
        
        assertFalse(context.files().stream().anyMatch(f -> f.path().startsWith("build/")));
        assertFalse(context.files().stream().anyMatch(f -> f.path().startsWith("node_modules/")));
    }
}