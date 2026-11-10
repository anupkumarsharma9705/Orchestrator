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

class EditFileToolTest {

    @TempDir
    Path workspace;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void editsFileAndReportsRelativeBackupPath() throws Exception {
        Path file = workspace.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class App { }");

        ToolResult result = edit("src/App.java", "class App { }", "class App { int value; }");

        assertTrue(result.success(), result.error());
        assertEquals("class App { int value; }", Files.readString(file));

        JsonNode output = mapper.readTree(result.output());
        String backupPath = output.path("backupPath").asText();
        assertFalse(Path.of(backupPath).isAbsolute());
        assertFalse(backupPath.contains(workspace.toString()));
        assertTrue(Files.exists(workspace.resolve(backupPath)));
        assertEquals("class App { }", Files.readString(workspace.resolve(backupPath)));
    }

    @Test
    void rejectsSymlinkedTarget() throws Exception {
        Path outside = Files.createTempDirectory("orchestrator-edit-outside-");
        Path secret = Files.writeString(outside.resolve("secret.java"), "class Secret { }");
        Path link = workspace.resolve("secret.java");
        Assumptions.assumeTrue(tryCreateSymlink(link, secret), "Symbolic links are not available");

        ToolResult result = edit("secret.java", "class Secret { }", "class Changed { }");

        assertFalse(result.success());
        assertTrue(result.error().contains("Path traversal"));
        assertEquals("class Secret { }", Files.readString(secret));
    }

    private ToolResult edit(String path, String oldText, String newText) throws Exception {
        String input = mapper.writeValueAsString(
            Map.of("path", path, "oldText", oldText, "newText", newText));
        return new EditFileTool(mapper).execute(project(), input);
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
