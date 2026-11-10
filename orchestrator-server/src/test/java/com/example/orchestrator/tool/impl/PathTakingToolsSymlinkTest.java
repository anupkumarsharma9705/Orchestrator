package com.example.orchestrator.tool.impl;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTakingToolsSymlinkTest {

    @TempDir
    Path workspace;

    @TempDir
    Path outside;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readFileRejectsSymlinkedFile() throws Exception {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = workspace.resolve("linked.txt");
        Assumptions.assumeTrue(tryCreateSymlink(link, secret), "Symbolic links are not available");

        ToolResult result = new ReadFileTool(mapper).execute(project(), "{\"path\":\"linked.txt\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
    }

    @Test
    void listDirectoryHidesSymlinks() throws Exception {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        Path linkedFile = workspace.resolve("linked.txt");
        Path linkedDirectory = workspace.resolve("linked-dir");
        Assumptions.assumeTrue(tryCreateSymlink(linkedFile, secret), "Symbolic links are not available");
        Assumptions.assumeTrue(tryCreateSymlink(linkedDirectory, outside), "Symbolic links are not available");

        ToolResult result = new ListDirectoryTool(mapper).execute(project(), "{\"maxDepth\":3}");

        assertTrue(result.success(), result.error());
        JsonNode entries = mapper.readTree(result.output()).path("entries");
        for (JsonNode entry : entries) {
            String path = entry.path("path").asText();
            assertFalse(path.equals("linked.txt"), path);
            assertFalse(path.startsWith("linked-dir"), path);
        }
    }

    @Test
    void searchFilesDoesNotReadThroughSymlinkedFile() throws Exception {
        Files.writeString(outside.resolve("leak.java"), "class SearchMarker {}");
        Path link = workspace.resolve("leak.java");
        Assumptions.assumeTrue(tryCreateSymlink(link, outside.resolve("leak.java")),
            "Symbolic links are not available");

        ToolResult result = new SearchFilesTool(mapper)
            .execute(project(), "{\"contentPattern\":\"SearchMarker\"}");

        assertTrue(result.success(), result.error());
        assertTrue(mapper.readTree(result.output()).path("matches").isEmpty());
    }

    @Test
    void secretScanDoesNotReadThroughSymlinkedFile() throws Exception {
        Files.writeString(outside.resolve("Leak.java"), "class Leak { String key = \"AKIAIOSFODNN7EXAMPLE\"; }");
        Path link = workspace.resolve("Leak.java");
        Assumptions.assumeTrue(tryCreateSymlink(link, outside.resolve("Leak.java")),
            "Symbolic links are not available");

        ToolResult result = new SecretScanTool(mapper).execute(project(), "{}");

        assertTrue(result.success(), result.error());
        assertTrue(mapper.readTree(result.output()).path("findings").isEmpty());
    }

    @Test
    void searchFilesRejectsSymlinkedSearchRoot() throws Exception {
        Path link = workspace.resolve("linked-dir");
        Assumptions.assumeTrue(tryCreateSymlink(link, outside), "Symbolic links are not available");

        ToolResult result = new SearchFilesTool(mapper)
            .execute(project(), "{\"path\":\"linked-dir\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
    }

    private Project project() {
        return new Project(
            "test",
            "Test",
            workspace,
            workspace.resolve(".ai"),
            "maven",
            "test",
            Map.of(),
            Instant.now(),
            Instant.now()
        );
    }

    private boolean tryCreateSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        }
    }
}
