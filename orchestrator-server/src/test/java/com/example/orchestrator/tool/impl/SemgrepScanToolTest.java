package com.example.orchestrator.tool.impl;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.sandbox.SandboxService;
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
class SemgrepScanToolTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteSemgrepScan() throws IOException {
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot.resolve("src/main/java"));
        Files.writeString(projectRoot.resolve("src/main/java/Unsafe.java"), 
            "public class Unsafe { public void test() { System.out.println(\"test\"); } }");
        Files.writeString(projectRoot.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test</artifactId>
                <version>1.0</version>
            </project>
            """);

        ObjectMapper mapper = new ObjectMapper();
        SemgrepScanTool tool = new SemgrepScanTool(mapper, org.mockito.Mockito.mock(SandboxService.class));
        
        Project project = new Project(
            "test", "Test", tempDir.resolve("test-project"), 
            tempDir.resolve("test-project").resolve(".ai"),
            "maven", "test", Map.of(), Instant.now(), Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{}");
        
        // Tool should execute (may succeed or fail depending on semgrep availability)
        // But should not crash
        assertNotNull(result);
        assertNotNull(result.toolName());
        assertEquals("semgrepScan", result.toolName());
    }
}