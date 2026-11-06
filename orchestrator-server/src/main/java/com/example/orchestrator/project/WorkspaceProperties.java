package com.example.orchestrator.project;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Allow-list of host directories that may be registered as projects.
 *
 * <p>The API accepts a {@code rootPath} from an authenticated caller. Without
 * an allow-list that input is an arbitrary directory read/write primitive.
 * Roots are canonicalised before comparison so symlinks cannot escape them.</p>
 */
@ConfigurationProperties(prefix = "orchestrator.workspace")
public class WorkspaceProperties {

    private List<String> allowedRoots = new ArrayList<>();

    public List<String> getAllowedRoots() {
        return allowedRoots;
    }

    public void setAllowedRoots(List<String> allowedRoots) {
        this.allowedRoots = allowedRoots == null ? new ArrayList<>() : allowedRoots;
    }

    /**
     * @return canonical existing roots, newest configuration order preserved
     */
    public List<Path> canonicalRoots() {
        List<Path> roots = new ArrayList<>();
        for (String configuredRoot : allowedRoots) {
            if (configuredRoot == null || configuredRoot.isBlank()) {
                continue;
            }
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (java.nio.file.Files.isDirectory(root)) {
                try {
                    roots.add(root.toRealPath());
                } catch (java.io.IOException ignored) {
                    // Unreadable roots cannot authorise anything.
                }
            }
        }
        return roots;
    }

    /**
     * Resolves a caller-supplied path and verifies it is a real directory below
     * one of the configured roots.
     *
     * @throws IllegalArgumentException when the path is missing or outside the allow-list
     */
    public Path requireAllowedDirectory(Path requested) {
        if (requested == null) {
            throw new IllegalArgumentException("Workspace root is required");
        }
        if (!requested.isAbsolute()) {
            throw new IllegalArgumentException("Workspace root must be an absolute path");
        }

        Path canonical;
        try {
            canonical = requested.toRealPath();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Workspace root does not exist or is not accessible: " + requested);
        }
        if (!java.nio.file.Files.isDirectory(canonical)) {
            throw new IllegalArgumentException("Workspace root is not a directory: " + requested);
        }

        List<Path> roots = canonicalRoots();
        if (roots.isEmpty()) {
            throw new IllegalStateException(
                "No workspace roots are configured. Set orchestrator.workspace.allowed-roots "
                    + "(ORCHESTRATOR_WORKSPACE_ALLOWED_ROOTS) to one or more existing directories.");
        }
        for (Path root : roots) {
            if (canonical.startsWith(root)) {
                return canonical;
            }
        }
        throw new IllegalArgumentException("Workspace root is outside the configured allow-list: " + requested);
    }

    /**
     * Returns a path relative to the most specific configured root, or the file
     * name when the path is not below a configured root. Never returns an
     * absolute host path.
     */
    public String toRelativeDisplayPath(Path path) {
        if (path == null) {
            return "";
        }
        Path absolute = path.toAbsolutePath().normalize();
        Optional<Path> best = Optional.empty();
        for (Path root : canonicalRoots()) {
            if (absolute.startsWith(root) && (best.isEmpty() || root.getNameCount() > best.get().getNameCount())) {
                best = Optional.of(root);
            }
        }
        if (best.isPresent()) {
            return best.get().relativize(absolute).toString();
        }
        Path fileName = absolute.getFileName();
        return fileName == null ? "" : fileName.toString();
    }
}
