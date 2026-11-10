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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileToolTest {

    @TempDir
    Path workspace;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsNewNestedPackageDirectories() throws Exception {
        String path = "src/main/java/com/example/library/controller/BookController.java";
        String content = "package com.example.library.controller;";

        ToolResult result = write(path, content);

        assertTrue(result.success(), result.error());
        assertEquals(content, Files.readString(workspace.resolve(path)));
        assertTrue(Files.isDirectory(workspace.resolve("src/main/java/com/example/library/controller")));
    }

    @Test
    void reportsBackupAsRelativeWorkspacePath() throws Exception {
        String path = "src/main/java/App.java";
        assertTrue(write(path, "first").success());

        ToolResult result = write(path, "second");

        assertTrue(result.success(), result.error());
        JsonNode output = mapper.readTree(result.output());
        String backupPath = output.path("backupPath").asText();
        assertFalse(backupPath.isBlank());
        assertFalse(Path.of(backupPath).isAbsolute());
        assertFalse(backupPath.contains(workspace.toString()));
        assertTrue(Files.exists(workspace.resolve(backupPath)));
        assertEquals("first", Files.readString(workspace.resolve(backupPath)));
    }

    @Test
    void traversalErrorDoesNotExposeAbsoluteWorkspacePath() throws Exception {
        ToolResult result = write("../../outside.txt", "blocked");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
        assertFalse(result.error().contains(workspace.toAbsolutePath().toString()));
    }

    @Test
    void rejectsSymlinkedDirectoryEscape() throws Exception {
        Path outside = Files.createTempDirectory("orchestrator-write-outside-");
        Path secret = Files.writeString(outside.resolve("secret.txt"), "unchanged");
        Path link = workspace.resolve("linked-dir");
        Assumptions.assumeTrue(tryCreateSymlink(link, outside), "Symbolic links are not available");

        ToolResult result = write("linked-dir/secret.txt", "overwritten");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
        assertEquals("unchanged", Files.readString(secret));
    }

    @Test
    void rejectsSymlinkedTargetFile() throws Exception {
        Path outside = Files.createTempDirectory("orchestrator-write-outside-");
        Path secret = Files.writeString(outside.resolve("secret.txt"), "unchanged");
        Path link = workspace.resolve("secret.txt");
        Assumptions.assumeTrue(tryCreateSymlink(link, secret), "Symbolic links are not available");

        ToolResult result = write("secret.txt", "overwritten");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
        assertEquals("unchanged", Files.readString(secret));
    }

    private ToolResult write(String path, String content) throws Exception {
        String input = mapper.writeValueAsString(Map.of("path", path, "content", content));
        return new WriteFileTool(mapper).execute(project(), input);
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
