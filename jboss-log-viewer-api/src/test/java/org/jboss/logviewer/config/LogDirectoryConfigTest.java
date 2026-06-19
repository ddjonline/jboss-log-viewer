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
 * Uses injectable env/system-property lookups so no real environment is touched.
 */
class LogDirectoryConfigTest {

    private static Function<String, String> lookup(Map<String, String> values) {
        return values::get;
    }

    @Test
    void envVarWinsOverSystemPropertyAndDefault(@TempDir Path envDir, @TempDir Path propDir) throws Exception {
        Map<String, String> env = new HashMap<>();
        Map<String, String> props = new HashMap<>();
        env.put(LogDirectoryConfig.ENV_SERVER, envDir.toString());
        props.put(LogDirectoryConfig.PROP_SERVER, propDir.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(props));

        assertEquals(envDir.toRealPath(), config.rootFor(LogSet.SERVER),
                "Environment variable must take precedence over the system property");
    }

    @Test
    void systemPropertyUsedWhenEnvVarAbsent(@TempDir Path propDir) throws Exception {
        Map<String, String> env = new HashMap<>();
        Map<String, String> props = new HashMap<>();
        props.put(LogDirectoryConfig.PROP_SERVER, propDir.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(props));

        assertEquals(propDir.toRealPath(), config.rootFor(LogSet.SERVER),
                "System property must be used when the environment variable is unset");
    }

    @Test
    void applicationFallsBackToServerWhenUnset(@TempDir Path serverDir) {
        Map<String, String> env = new HashMap<>();
        Map<String, String> props = new HashMap<>();
        env.put(LogDirectoryConfig.ENV_SERVER, serverDir.toString());
        // No application override configured.

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(props));

        assertEquals(config.rootFor(LogSet.SERVER), config.rootFor(LogSet.APPLICATION),
                "Application root must fall back to the server root when unset");
    }

    @Test
    void distinctApplicationRootIsHonoured(@TempDir Path serverDir, @TempDir Path appDir) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put(LogDirectoryConfig.ENV_SERVER, serverDir.toString());
        env.put(LogDirectoryConfig.ENV_APPLICATION, appDir.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(Map.of()));

        assertEquals(appDir.toRealPath(), config.rootFor(LogSet.APPLICATION));
        assertEquals(serverDir.toRealPath(), config.rootFor(LogSet.SERVER));
    }

    @Test
    void missingDirectoryIsFlaggedNotThrown(@TempDir Path base) {
        Path missing = base.resolve("does-not-exist");
        Map<String, String> env = new HashMap<>();
        env.put(LogDirectoryConfig.ENV_SERVER, missing.toString());

        // Construction must not throw for a missing directory...
        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(Map.of()));

        // ...but the root must be reported as unusable.
        assertFalse(config.isUsable(LogSet.SERVER),
                "A missing directory must be flagged as unusable");
    }

    @Test
    void existingDirectoryIsUsableAndCanonicalized(@TempDir Path base) throws Exception {
        // Use a non-normalized path with a redundant "." segment.
        Path serverDir = Files.createDirectory(base.resolve("log"));
        Path messyPath = base.resolve(".").resolve("log");

        Map<String, String> env = new HashMap<>();
        env.put(LogDirectoryConfig.ENV_SERVER, messyPath.toString());

        LogDirectoryConfig config = LogDirectoryConfig.resolve(lookup(env), lookup(Map.of()));

        assertTrue(config.isUsable(LogSet.SERVER));
        assertEquals(serverDir.toRealPath(), config.rootFor(LogSet.SERVER),
                "Resolved root must be canonicalized (normalized + real path)");
    }
}
