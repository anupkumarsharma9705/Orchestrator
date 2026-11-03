package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListDirectoryToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldListDirectoryContents() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Controller.java"), "public class Controller {}");
        Files.writeString(srcDir.resolve("Service.java"), "public class Service {}");
        
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("target/output.txt"), "build output");
        
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git/config"), "git config");

        ObjectMapper mapper = new ObjectMapper();
        ListDirectoryTool tool = new ListDirectoryTool(mapper);
        
        Project project = new Project(
            "test",
            "Test",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{\"maxDepth\": 7}");
        
        assertTrue(result.success());
        assertNull(result.error());
        
        // Parse the JSON output
        String output = result.output();
        ListDirectoryTool.ListDirectoryOutput parsed = mapper.readValue(output, ListDirectoryTool.ListDirectoryOutput.class);
        
        List<String> paths = parsed.entries().stream().map(e -> e.path()).toList();
        
        List<String> controllerPaths = paths.stream().filter(p -> p.contains("Controller.java")).toList();
        System.out.println("All paths: " + paths);
        System.out.println("Controller paths: " + controllerPaths);
        assertTrue(controllerPaths.size() > 0, "Expected to find Controller.java in paths: " + paths);
        assertTrue(paths.stream().anyMatch(p -> p.contains("Service.java")));
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("src" + File.separator)));
        assertFalse(paths.stream().anyMatch(p -> p.startsWith("target/")));
        assertFalse(paths.stream().anyMatch(p -> p.startsWith(".git/")));
    }

    @Test
    void shouldRejectPathTraversal() {
        ObjectMapper mapper = new ObjectMapper();
        ListDirectoryTool tool = new ListDirectoryTool(mapper);
        
        Project project = new Project(
            "test",
            "Test",
            tempDir,
            tempDir.resolve(".ai"),
            "maven",
            "test",
            Map.of(),
            Instant.now(),
            Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{\"path\": \"../../etc/passwd\"}");
        
        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Path traversal"));
    }
}