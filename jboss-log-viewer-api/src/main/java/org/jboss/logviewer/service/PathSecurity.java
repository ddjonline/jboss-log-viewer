package org.jboss.logviewer.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Confines client-supplied relative paths to a configured root directory.
 *
 * <p>This is the single most important control in the application: every
 * filesystem operation that takes a path from the client must route it through
 * {@link #resolve(Path, String)}. It blocks {@code ../} traversal, absolute
 * paths, and symlink escapes (a symlink whose real target lies outside the root).
 *
 * <p>The class is stateless and container-free, so it is trivially unit-testable.
 */
public final class PathSecurity {

    /**
     * Resolves {@code relativePath} against {@code root} and returns the
     * canonical target, guaranteed to lie within {@code root}.
     *
     * <p>Resolution steps:
     * <ol>
     *   <li>Reject {@code null}/blank input and any absolute path.</li>
     *   <li>Resolve the relative path against the root.</li>
     *   <li>Canonicalize the root and the target (following symlinks to their
     *       real targets when they exist; normalizing otherwise).</li>
     *   <li>Reject unless the canonical target still starts with the canonical
     *       root.</li>
     * </ol>
     *
     * @param root         the configured root directory for a {@code LogSet}
     * @param relativePath a client-supplied path relative to {@code root}
     * @return the canonical, confined {@link Path}
     * @throws PathSecurityException if the input is invalid or escapes the root
     */
    public Path resolve(Path root, String relativePath) {
        if (root == null) {
            throw new PathSecurityException("No root directory configured");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new PathSecurityException("Path must not be empty");
        }

        Path requested = Path.of(relativePath);
        if (requested.isAbsolute()) {
            throw new PathSecurityException("Absolute paths are not allowed: " + relativePath);
        }

        Path canonicalRoot = canonicalize(root);
        Path resolved = canonicalize(canonicalRoot.resolve(requested));

        if (!resolved.startsWith(canonicalRoot)) {
            throw new PathSecurityException("Path escapes the configured root: " + relativePath);
        }
        return resolved;
    }

    /**
     * Canonicalizes a path: resolves symlinks and real casing via
     * {@link Path#toRealPath} when the path exists, otherwise falls back to a
     * normalized absolute path (so non-existent targets are still confined).
     */
    private static Path canonicalize(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return normalized;
        }
    }
}
