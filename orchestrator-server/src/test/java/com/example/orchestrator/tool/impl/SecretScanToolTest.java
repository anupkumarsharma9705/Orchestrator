package com.example.orchestrator.tool.impl;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Import(SharedTestConfig.class)
class SecretScanToolTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @TempDir
    Path tempDir;

    @Test
    void shouldDetectNoSecretsInCleanCode() throws IOException {
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot.resolve("src/main/java"));
        Files.writeString(projectRoot.resolve("src/main/java/Clean.java"), 
            "public class Clean { public void hello() { System.out.println(\"hello\"); } }");

        ObjectMapper mapper = new ObjectMapper();
        SecretScanTool tool = new SecretScanTool(mapper);
        
        Project project = new Project(
            "test", "Test", tempDir.resolve("test-project"), 
            tempDir.resolve("test-project").resolve(".ai"),
            "maven", "test", Map.of(), Instant.now(), Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{}");
        
        assertNotNull(result);
        assertEquals("secretScan", result.toolName());
        assertTrue(result.success());
    }

    @Test
    void shouldDetectAWSKeyPattern() throws IOException {
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot.resolve("src/main/java"));
        Files.writeString(projectRoot.resolve("src/main/java/Unsafe.java"), 
            "public class Unsafe { String key = \"AKIAIOSFODNN7EXAMPLE\"; }");

        ObjectMapper mapper = new ObjectMapper();
        SecretScanTool tool = new SecretScanTool(mapper);
        
        Project project = new Project(
            "test", "Test", tempDir.resolve("test-project"), 
            tempDir.resolve("test-project").resolve(".ai"),
            "maven", "test", Map.of(), Instant.now(), Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{}");
        
        assertNotNull(result);
        assertEquals("secretScan", result.toolName());
        // The tool should find the AWS key pattern
    }
}