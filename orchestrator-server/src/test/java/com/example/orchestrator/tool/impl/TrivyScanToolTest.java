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
class TrivyScanToolTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteTrivyScan() throws IOException {
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot.resolve("src/main/java"));
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
        TrivyScanTool tool = new TrivyScanTool(mapper);
        
        Project project = new Project(
            "test", "Test", tempDir.resolve("test-project"), 
            tempDir.resolve("test-project").resolve(".ai"),
            "maven", "test", Map.of(), Instant.now(), Instant.now()
        );
        
        ToolResult result = tool.execute(project, "{\"target\": \"fs\"}");
        
        assertNotNull(result);
        assertEquals("trivyScan", result.toolName());
    }
}