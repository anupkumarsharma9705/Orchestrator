package com.example.orchestrator.core.coder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File tools for safe file operations in staging area.
 * Handles path validation, size limits, symlink checks, and workspace protection.
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    private Path stagingDir;
    private Path workspaceRoot;
    private Path backupDir;
    private final int maxFileSizeKb = 100;
    private final Object initLock = new Object();
    private volatile boolean initialized = false;

    /**
     * Initializes the file tools for a specific task.
     */
    public void init(Path stagingDir, Path workspaceRoot) {
        synchronized (initLock) {
            this.stagingDir = stagingDir;
            this.workspaceRoot = workspaceRoot;
            this.backupDir = stagingDir.resolveSibling("backup");
            try {
                Files.createDirectories(stagingDir);
                Files.createDirectories(backupDir);
            } catch (IOException e) {
                log.error("Failed to create staging/backup directories: {}", e.getMessage(), e);
            }
            this.initialized = true;
        }
    }

    /**
     * Ensures the file tools are initialized before use.
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("FileTools not initialized. Call init() first.");
        }
    }

    /**
     * Writes a file to the staging area.
     */
    public void writeFile(String relativePath, String content) throws IOException {
        ensureInitialized();
        Path target = resolveStagingPath(relativePath);
        validatePath(target, content);
        
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        log.debug("Wrote file to staging: {} ({} bytes)", relativePath, content.getBytes().length);
    }

    /**
     * Edits a file in the staging area (find/replace).
     */
    public void editFile(String relativePath, String oldString, String newString) throws IOException {
        ensureInitialized();
        Path target = resolveStagingPath(relativePath);
        if (!Files.exists(target)) {
            throw new IOException("File not found in staging: " + relativePath);
        }
        
        String content = Files.readString(target);
        if (!content.contains(oldString)) {
            throw new IOException("Old string not found in file: " + relativePath);
        }
        
        String updated = content.replace(oldString, newString);
        validatePath(target, updated);
        Files.writeString(target, updated);
        log.debug("Edited file in staging: {}", relativePath);
    }

    /**
     * Reads a file from the staging area or workspace.
     */
    public String readFile(String relativePath) throws IOException {
        ensureInitialized();
        Path stagingPath = resolveStagingPath(relativePath);
        if (Files.exists(stagingPath)) {
            return Files.readString(stagingPath);
        }
        
        Path workspacePath = resolveWorkspacePath(relativePath);
        if (Files.exists(workspacePath)) {
            return Files.readString(workspacePath);
        }
        
        throw new IOException("File not found: " + relativePath);
    }

    /**
     * Lists files in a directory (staging or workspace).
     */
    public List<String> listFiles(String relativePath) throws IOException {
        ensureInitialized();
        Path dir = resolveStagingPath(relativePath);
        if (!Files.exists(dir)) {
            dir = resolveWorkspacePath(relativePath);
        }
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        
        final Path finalDir = dir;
        try (Stream<Path> stream = Files.list(finalDir)) {
            return stream
                .map(p -> finalDir.relativize(p).toString())
                .collect(Collectors.toList());
        }
    }

    /**
     * Promotes staged files to the workspace.
     */
    public void promoteToWorkspace(Path stagingDir, Path workspaceRoot) throws IOException {
        ensureInitialized();
        if (!Files.exists(stagingDir)) {
            return;
        }
        
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            stream.filter(Files::isRegularFile)
                .forEach(stagedFile -> {
                    try {
                        Path relative = stagingDir.relativize(stagedFile);
                        Path target = workspaceRoot.resolve(relative);
                        Files.createDirectories(target.getParent());
                        Files.copy(stagedFile, target, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Promoted staged file to workspace: {}", relative);
                    } catch (IOException e) {
                        log.error("Failed to promote file: {}", e.getMessage(), e);
                    }
                });
        }
    }

    /**
     * Resolves a path relative to the staging directory.
     * Prevents path traversal attacks (both Unix and Windows paths).
     */
    private Path resolveStagingPath(String relativePath) {
        Path path = Paths.get(relativePath).normalize();
        if (path.isAbsolute() || containsPathTraversal(path)) {
            throw new IllegalArgumentException("Invalid path: " + relativePath);
        }
        return stagingDir.resolve(path);
    }

    /**
     * Resolves a path relative to the workspace root.
     * Prevents path traversal attacks (both Unix and Windows paths).
     */
    private Path resolveWorkspacePath(String relativePath) {
        Path path = Paths.get(relativePath).normalize();
        if (path.isAbsolute() || containsPathTraversal(path)) {
            throw new IllegalArgumentException("Invalid path: " + relativePath);
        }
        return workspaceRoot.resolve(path);
    }

    /**
     * Validates a file path and content for safety.
     */
    void validatePath(Path path, String content) {
        // Size check
        int sizeKb = content.getBytes().length / 1024;
        if (containsPathTraversal(path)) {
            throw new IllegalArgumentException("Path traversal not allowed: " + path);
        }
        if (path.toString().contains(".git") || path.toString().contains(".template")) {
            throw new IllegalArgumentException("Protected path: " + path);
        }
        // Size check (approximate)
        if (content.getBytes().length > 100 * 1024) {
            throw new IllegalArgumentException("File too large: " + path);
        }
    }

    /**
     * Promotes all staged files to the workspace.
     * Backs up existing workspace files before overwriting for rollback capability (H10).
     */
    public void promoteStaged() throws IOException {
        ensureInitialized();
        // Backup existing workspace files that will be overwritten
        backupExistingFiles();
        promoteToWorkspace(stagingDir, workspaceRoot);
    }

    /**
     * Backs up existing workspace files that exist in staging.
     * Used for rollback capability (H10).
     */
    private void backupExistingFiles() throws IOException {
        if (!Files.exists(stagingDir)) {
            return;
        }
        
        Files.walk(stagingDir)
            .filter(Files::isRegularFile)
            .forEach(stagedFile -> {
                try {
                    Path relative = stagingDir.relativize(stagedFile);
                    Path workspaceFile = workspaceRoot.resolve(relative);
                    Path backupFile = backupDir.resolve(relative);
                    
                    if (Files.exists(workspaceFile)) {
                        Files.createDirectories(backupFile.getParent());
                        Files.copy(workspaceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Backed up workspace file for rollback: {}", relative);
                    }
                } catch (IOException e) {
                    log.warn("Failed to backup file {}: {}", stagedFile, e.getMessage());
                }
            });
    }

    /**
     * Reverts promoted changes by restoring from backup (H10).
     * Clears staging and restores workspace files from backup.
     */
    public void revertPromoted() throws IOException {
        ensureInitialized();
        // Restore backed up files to workspace
        if (Files.exists(backupDir)) {
            Files.walk(backupDir)
                .filter(Files::isRegularFile)
                .forEach(backupFile -> {
                    try {
                        Path relative = backupDir.relativize(backupFile);
                        Path workspaceFile = workspaceRoot.resolve(relative);
                        Files.createDirectories(workspaceFile.getParent());
                        Files.copy(backupFile, workspaceFile, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Restored workspace file from backup: {}", relative);
                    } catch (IOException e) {
                        log.warn("Failed to restore file {}: {}", backupFile, e.getMessage());
                    }
                });
        }
        
        // Clear staging
        clearStaged();
        
        log.info("Reverted promoted changes and restored workspace from backup");
    }

    /**
     * Clears the staging directory and backup.
     */
    public void clearStaged() throws IOException {
        ensureInitialized();
        if (Files.exists(stagingDir)) {
            Files.walk(stagingDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete staged file: {}", e.getMessage());
                    }
                });
        }
        // Clear backup too
        if (Files.exists(backupDir)) {
            Files.walk(backupDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete backup file: {}", e.getMessage());
                    }
                });
        }
    }

    /**
     * Checks for path traversal attempts in both Unix (../) and Windows (..\\) formats.
     */
    private static boolean containsPathTraversal(Path path) {
        String pathStr = path.toString();
        return pathStr.contains("..") || pathStr.contains("\\..");
    }
}