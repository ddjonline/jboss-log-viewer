package org.jboss.logviewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LogDirectoryConfig} resolution and usability handling.
 * Uses an injectable JNDI lookup so no real naming provider is touched.
 */
class LogDirectoryConfigTest {

    private static Function<String, String> lookup(Map<String, String> values) {
        return values::get;
    }

    @Test
    void serverJndiBindingWinsOverDefault(@TempDir Path jndiDir) throws Exception {
        Map<String, String> jndi = new HashMap<>();
        jndi.put(LogDirectoryConfig.JNDI_SERVER, jndiDir.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(jndi));

        assertEquals(jndiDir.toRealPath(), config.rootFor(LogSet.SERVER),
                "JNDI binding must take precedence over the default");
    }

    @Test
    void defaultsUsedWhenJndiBindingsAbsent() {
        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(Map.of()));

        assertEquals(Path.of(LogDirectoryConfig.DEFAULT_SERVER).toAbsolutePath().normalize(),
                config.rootFor(LogSet.SERVER));
        assertEquals(Path.of(LogDirectoryConfig.DEFAULT_APPLICATION).toAbsolutePath().normalize(),
                config.rootFor(LogSet.APPLICATION));
    }

    @Test
    void blankJndiBindingUsesDefault() {
        Map<String, String> jndi = new HashMap<>();
        jndi.put(LogDirectoryConfig.JNDI_APPLICATION, " ");

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(jndi));

        assertEquals(Path.of(LogDirectoryConfig.DEFAULT_APPLICATION).toAbsolutePath().normalize(),
                config.rootFor(LogSet.APPLICATION));
    }

    @Test
    void distinctApplicationRootIsHonoured(@TempDir Path serverDir, @TempDir Path appDir) throws Exception {
        Map<String, String> jndi = new HashMap<>();
        jndi.put(LogDirectoryConfig.JNDI_SERVER, serverDir.toString());
        jndi.put(LogDirectoryConfig.JNDI_APPLICATION, appDir.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(jndi));

        assertEquals(appDir.toRealPath(), config.rootFor(LogSet.APPLICATION));
        assertEquals(serverDir.toRealPath(), config.rootFor(LogSet.SERVER));
    }

    @Test
    void missingDirectoryIsFlaggedNotThrown(@TempDir Path base) {
        Path missing = base.resolve("does-not-exist");
        Map<String, String> jndi = new HashMap<>();
        jndi.put(LogDirectoryConfig.JNDI_SERVER, missing.toString());

        // Construction must not throw for a missing directory...
        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(jndi));

        // ...but the root must be reported as unusable.
        assertFalse(config.isUsable(LogSet.SERVER),
                "A missing directory must be flagged as unusable");
    }

    @Test
    void existingDirectoryIsUsableAndCanonicalized(@TempDir Path base) throws Exception {
        // Use a non-normalized path with a redundant "." segment.
        Path serverDir = Files.createDirectory(base.resolve("log"));
        Path messyPath = base.resolve(".").resolve("log");

        Map<String, String> jndi = new HashMap<>();
        jndi.put(LogDirectoryConfig.JNDI_SERVER, messyPath.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(jndi));

        assertTrue(config.isUsable(LogSet.SERVER));
        assertEquals(serverDir.toRealPath(), config.rootFor(LogSet.SERVER),
                "Resolved root must be canonicalized (normalized + real path)");
    }
}
