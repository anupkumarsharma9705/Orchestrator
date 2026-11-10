package com.example.orchestrator.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Resolves tool-supplied paths against a project workspace and blocks escapes.
 *
 * <p>Lexical normalisation alone is not enough: a symbolic link inside the
 * workspace can point anywhere on the host. This guard canonicalises the
 * nearest existing ancestor and rejects symbolic links below the workspace
 * root. The target itself may not exist yet (for example when {@code writeFile}
 * creates a new package directory), so validation is performed against the
 * existing prefix and repeated after the parent directories are created.</p>
 */
public final class WorkspacePathGuard {

    public static final String TRAVERSAL_MESSAGE = "Path traversal attempt denied";

    private WorkspacePathGuard() {
    }

    /**
     * Resolves a user-supplied path and verifies that its existing prefix is
     * inside the workspace. The returned path may not exist yet, but no
     * existing component may be a symbolic link.
     *
     * @throws PathViolationException when the path escapes the workspace, uses
     *                                a symbolic link, or contains invalid syntax
     */
    public static Path resolve(Path workspaceRoot, String requestedPath) throws PathViolationException {
        Path lexicalRoot = lexicalRoot(workspaceRoot);
        Path target = resolveTarget(lexicalRoot, requestedPath);

        if (!target.startsWith(lexicalRoot)) {
            throw new PathViolationException(TRAVERSAL_MESSAGE);
        }

        Path realRoot = realRoot(lexicalRoot);
        Path existingAncestor = target;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null || !existingAncestor.startsWith(lexicalRoot)) {
            throw new PathViolationException(TRAVERSAL_MESSAGE);
        }

        rejectSymlinks(lexicalRoot, existingAncestor);
        requireInsideRealRoot(existingAncestor, realRoot);

        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymlinks(lexicalRoot, target);
            requireInsideRealRoot(target, realRoot);
        }

        return target;
    }

    /**
     * Creates the target's parent directories and re-verifies the path after
     * creation. This closes the window where a directory could be replaced by a
     * symbolic link between validation and the write.
     */
    public static void createParentDirectories(Path workspaceRoot, Path target)
            throws IOException, PathViolationException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new PathViolationException("Invalid path");
        }
        Files.createDirectories(parent);
        verifyParent(workspaceRoot, target);
    }

    /**
     * Re-validates an existing target and its parent immediately before use.
     */
    public static void verifyParent(Path workspaceRoot, Path target) throws PathViolationException {
        Path lexicalRoot = lexicalRoot(workspaceRoot);
        Path normalised = target.toAbsolutePath().normalize();
        if (!normalised.startsWith(lexicalRoot) || normalised.equals(lexicalRoot)) {
            throw new PathViolationException(TRAVERSAL_MESSAGE);
        }

        Path parent = normalised.getParent();
        if (parent == null || !parent.startsWith(lexicalRoot)) {
            throw new PathViolationException("Project path is not accessible");
        }

        rejectSymlinks(lexicalRoot, normalised);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new PathViolationException("Project path is not accessible");
        }

        Path realRoot = realRoot(lexicalRoot);
        requireInsideRealRoot(parent, realRoot);

        if (Files.exists(normalised, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymlinks(lexicalRoot, normalised);
            requireInsideRealRoot(normalised, realRoot);
        }
    }

    private static Path lexicalRoot(Path workspaceRoot) throws PathViolationException {
        if (workspaceRoot == null) {
            throw new PathViolationException("Project workspace is not accessible");
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new PathViolationException("Project workspace is not accessible");
        }
        return root;
    }

    private static Path realRoot(Path lexicalRoot) throws PathViolationException {
        try {
            return lexicalRoot.toRealPath();
        } catch (IOException e) {
            throw new PathViolationException("Project workspace is not accessible");
        }
    }

    private static Path resolveTarget(Path lexicalRoot, String requestedPath) throws PathViolationException {
        try {
            if (requestedPath == null || requestedPath.isBlank()) {
                return lexicalRoot;
            }
            return lexicalRoot.resolve(requestedPath).normalize();
        } catch (InvalidPathException e) {
            throw new PathViolationException("Invalid path");
        }
    }

    private static void rejectSymlinks(Path lexicalRoot, Path candidate) throws PathViolationException {
        Path current = lexicalRoot;
        for (Path part : lexicalRoot.relativize(candidate)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new PathViolationException(TRAVERSAL_MESSAGE);
            }
        }
    }

    private static void requireInsideRealRoot(Path candidate, Path realRoot) throws PathViolationException {
        Path realCandidate;
        try {
            realCandidate = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new PathViolationException("Project path is not accessible");
        }
        if (!realCandidate.startsWith(realRoot)) {
            throw new PathViolationException(TRAVERSAL_MESSAGE);
        }
    }

    /**
     * Checked violation with a caller-safe message. Messages never contain
     * absolute host paths.
     */
    public static final class PathViolationException extends Exception {
        public PathViolationException(String message) {
            super(message);
        }
    }
}
