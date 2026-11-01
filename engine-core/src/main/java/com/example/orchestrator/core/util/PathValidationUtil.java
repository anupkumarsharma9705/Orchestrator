package com.example.orchestrator.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Shared path validation utilities.
 * Prevents path traversal attacks and validates workspace boundaries.
 */
public final class PathValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(PathValidationUtil.class);

    // Allowed workspace base directory - all workspace paths must be under this
    private static final Path ALLOWED_WORKSPACE_BASE = Paths.get(System.getProperty("user.home"), ".orchestrator", "workspaces").toAbsolutePath().normalize();
    
    // Pattern to detect path traversal attempts (cross-platform)
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(^|[/\\\\\\\\])\\\\.(?:\\\\.|$)");
    
    // Windows reserved names that shouldn't be used in paths
    private static final Pattern WINDOWS_RESERVED_PATTERN = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\\\..*)?$");

    private PathValidationUtil() {
        // Utility class
    }

    /**
     * Validates that a workspace path is within the allowed base directory.
     * Prevents path traversal attacks.
     * 
     * @param workspacePath The path to validate
     * @return The validated absolute path, or null if validation fails
     */
    public static Path validateWorkspacePath(Path workspacePath) {
        try {
            Path absolutePath = workspacePath.toAbsolutePath().normalize();
            
            // Check if path is within allowed base directory
            if (!absolutePath.startsWith(ALLOWED_WORKSPACE_BASE)) {
                log.warn("Workspace path outside allowed base: {} (base: {})", absolutePath, ALLOWED_WORKSPACE_BASE);
                return null;
            }
            
            // Check for path traversal attempts
            String pathStr = absolutePath.toString();
            if (PATH_TRAVERSAL_PATTERN.matcher(pathStr).find()) {
                log.warn("Path traversal attempt detected: {}", pathStr);
                return null;
            }
            
            // Check for Windows reserved names
            for (Path name : absolutePath) {
                if (WINDOWS_RESERVED_PATTERN.matcher(name.toString()).matches()) {
                    log.warn("Windows reserved name in path: {}", name);
                    return null;
                }
            }
            
            // Ensure directory exists
            if (!Files.exists(absolutePath)) {
                log.warn("Workspace path does not exist: {}", absolutePath);
                return null;
            }
            
            // Ensure it's a directory
            if (!Files.isDirectory(absolutePath)) {
                log.warn("Workspace path is not a directory: {}", absolutePath);
                return null;
            }
            
            return absolutePath;
            
        } catch (Exception e) {
            log.warn("Workspace path validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the allowed workspace base directory.
     */
    public static Path getAllowedWorkspaceBase() {
        return ALLOWED_WORKSPACE_BASE;
    }

    /**
     * Checks if a path string contains path traversal attempts.
     */
    public static boolean containsPathTraversal(String pathStr) {
        return PATH_TRAVERSAL_PATTERN.matcher(pathStr).find();
    }

    /**
     * Checks if a path component is a Windows reserved name.
     */
    public static boolean isWindowsReservedName(String name) {
        return WINDOWS_RESERVED_PATTERN.matcher(name).matches();
    }
}