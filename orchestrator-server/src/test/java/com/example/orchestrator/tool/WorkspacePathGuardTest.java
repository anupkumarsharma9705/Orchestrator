package com.example.orchestrator.tool;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePathGuardTest {

    @TempDir
    Path workspace;

    @TempDir
    Path outside;

    @Test
    void resolvesExistingFile() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Path file = Files.writeString(workspace.resolve("src/App.java"), "class App {}");

        Path resolved = WorkspacePathGuard.resolve(workspace, "src/App.java");

        assertEquals(file.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void resolvesNewNestedPathWhoseParentsDoNotExist() throws WorkspacePathGuard.PathViolationException {
        Path expected = workspace.resolve("src/main/java/com/example/New.java").toAbsolutePath().normalize();

        Path resolved = WorkspacePathGuard.resolve(workspace, "src/main/java/com/example/New.java");

        assertEquals(expected, resolved);
    }

    @Test
    void rejectsLexicalTraversal() {
        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.resolve(workspace, "../outside.txt"));

        assertEquals(WorkspacePathGuard.TRAVERSAL_MESSAGE, error.getMessage());
    }

    @Test
    void rejectsAbsolutePathOutsideWorkspace() {
        Path absoluteOutside = outside.resolve("secret.txt").toAbsolutePath();

        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.resolve(workspace, absoluteOutside.toString()));

        assertEquals(WorkspacePathGuard.TRAVERSAL_MESSAGE, error.getMessage());
    }

    @Test
    void rejectsSymlinkedFile() throws IOException {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = workspace.resolve("linked-secret.txt");
        Assumptions.assumeTrue(tryCreateSymlink(link, secret), "Symbolic links are not available");

        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.resolve(workspace, "linked-secret.txt"));

        assertEquals(WorkspacePathGuard.TRAVERSAL_MESSAGE, error.getMessage());
    }

    @Test
    void rejectsSymlinkedDirectory() throws IOException {
        Path secret = Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = workspace.resolve("linked-dir");
        Assumptions.assumeTrue(tryCreateSymlink(link, secret.getParent()), "Symbolic links are not available");

        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.resolve(workspace, "linked-dir/secret.txt"));

        assertEquals(WorkspacePathGuard.TRAVERSAL_MESSAGE, error.getMessage());
    }

    @Test
    void rejectsMissingWorkspaceRoot() {
        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.resolve(workspace.resolve("missing"), "file.txt"));

        assertTrue(error.getMessage().contains("workspace"));
        assertTrue(!error.getMessage().contains(workspace.toString()));
    }

    @Test
    void createsNestedParentDirectoriesAndWritesInsideWorkspace() throws Exception {
        Path target = WorkspacePathGuard.resolve(
            workspace, "src/main/java/com/example/library/controller/BookController.java");

        WorkspacePathGuard.createParentDirectories(workspace, target);
        Files.writeString(target, "package com.example.library.controller;");

        assertTrue(Files.isDirectory(target.getParent()));
        assertEquals("package com.example.library.controller;", Files.readString(target));
    }

    @Test
    void recheckRejectsSymlinkedParentCreatedAfterResolution() throws Exception {
        Path target = WorkspacePathGuard.resolve(workspace, "linked-dir/New.java");
        Path secretDirectory = Files.createDirectories(outside.resolve("secret-dir"));
        Assumptions.assumeTrue(tryCreateSymlink(workspace.resolve("linked-dir"), secretDirectory),
            "Symbolic links are not available");

        WorkspacePathGuard.PathViolationException error = assertThrows(
            WorkspacePathGuard.PathViolationException.class,
            () -> WorkspacePathGuard.verifyParent(workspace, target));

        assertEquals(WorkspacePathGuard.TRAVERSAL_MESSAGE, error.getMessage());
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
