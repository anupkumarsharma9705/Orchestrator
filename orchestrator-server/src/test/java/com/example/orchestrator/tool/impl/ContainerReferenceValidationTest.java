package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerReferenceValidationTest {

    @TempDir
    Path tempDir;

    private Project project() {
        return new Project(
            "test", "Test", tempDir, tempDir.resolve(".ai"),
            "maven", "test", Map.of(), Instant.now(), Instant.now()
        );
    }

    @Test
    void dockerImageScanRejectsOptionLikeReferences() {
        DockerImageScanTool tool = new DockerImageScanTool(new ObjectMapper());

        ToolResult result = tool.execute(project(), "{\"image\": \"--output=/tmp/report\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("Invalid image reference"));
    }

    @Test
    void dockerImageScanRejectsShellMetacharacters() {
        DockerImageScanTool tool = new DockerImageScanTool(new ObjectMapper());

        ToolResult result = tool.execute(project(), "{\"image\": \"alpine;curl evil\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("Invalid image reference"));
    }

    @Test
    void trivyScanRejectsOptionLikeTargets() {
        TrivyScanTool tool = new TrivyScanTool(new ObjectMapper());

        ToolResult result = tool.execute(project(), "{\"target\": \"--config=/tmp/evil.yaml\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("Invalid image reference"));
    }
}
