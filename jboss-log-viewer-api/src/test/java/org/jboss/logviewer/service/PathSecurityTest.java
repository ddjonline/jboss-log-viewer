package org.jboss.logviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link PathSecurity} — the path-traversal guard. All cases run
 * against a JUnit {@code @TempDir}; no container is involved.
 */
class PathSecurityTest {

    private final PathSecurity security = new PathSecurity();

    @Test
    void acceptsValidNestedPath(@TempDir Path root) throws Exception {
        Path nested = Files.createDirectories(root.resolve("subdir"));
        Files.writeString(nested.resolve("app.log"), "hello");

        Path resolved = security.resolve(root, "subdir/app.log");

        assertTrue(resolved.startsWith(root.toRealPath()),
                "A valid nested path must resolve under the root");
        assertEquals("app.log", resolved.getFileName().toString());
    }

    @Test
    void acceptsDirectChildOfRoot(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("server.log"), "data");

        Path resolved = security.resolve(root, "server.log");

        assertTrue(resolved.startsWith(root.toRealPath()));
    }

    @Test
    void rejectsParentTraversal(@TempDir Path root) {
        assertThrows(PathSecurityException.class,
                () -> security.resolve(root, "../outside.log"));
        assertThrows(PathSecurityException.class,
                () -> security.resolve(root, "subdir/../../outside.log"));
    }

    @Test
    void rejectsAbsolutePath(@TempDir Path root) {
        assertThrows(PathSecurityException.class,
                () -> security.resolve(root, "/etc/passwd"));
    }

    @Test
    void rejectsNullOrBlank(@TempDir Path root) {
        assertThrows(PathSecurityException.class, () -> security.resolve(root, null));
        assertThrows(PathSecurityException.class, () -> security.resolve(root, ""));
        assertThrows(PathSecurityException.class, () -> security.resolve(root, "   "));
    }

    @Test
    void rejectsNullRoot() {
        assertThrows(PathSecurityException.class, () -> security.resolve(null, "x.log"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // POSIX symlinks
    void rejectsSymlinkEscapingRoot(@TempDir Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("root"));
        Path outside = Files.createDirectory(base.resolve("outside"));
        Files.writeString(outside.resolve("secret.log"), "secret");

        // A symlink inside the root that points outside it.
        Path link = root.resolve("escape");
        Files.createSymbolicLink(link, outside);

        assertThrows(PathSecurityException.class,
                () -> security.resolve(root, "escape/secret.log"),
                "A symlink whose target resolves outside the root must be rejected");
    }

    @Test
    void allowsNonExistentFileUnderRoot(@TempDir Path root) throws Exception {
        // A poll/tail of a not-yet-created file must still be confined, not rejected.
        Path resolved = security.resolve(root, "future.log");
        assertTrue(resolved.startsWith(root.toRealPath()));
    }
}
