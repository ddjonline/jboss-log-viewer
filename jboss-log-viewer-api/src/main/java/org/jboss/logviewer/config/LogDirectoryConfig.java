package org.jboss.logviewer.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and holds the two log root directories.
 *
 * <p>Resolution per root follows the order <strong>environment variable &rarr;
 * JVM system property &rarr; default</strong>:
 * <ul>
 *   <li>Server: {@code JBOSS_SERVER_LOG_DIR} &rarr; {@code jboss.server.log.dir}
 *       &rarr; default (if any).</li>
 *   <li>Application: {@code JBOSS_APP_LOG_DIR} &rarr; {@code app.log.dir} &rarr;
 *       falls back to the resolved server root.</li>
 * </ul>
 *
 * <p>The resolution logic is exposed through {@link #resolve(Function, Function)}
 * with injectable lookups so it can be unit-tested without touching the real
 * process environment. The {@code (Path, Path)} constructor lets tests build a
 * config directly from temp directories.
 *
 * <p>A root that is missing or not a readable directory is retained (so the rest
 * of the application can report it) but flagged via {@link #isUsable(LogSet)};
 * construction never throws for that reason.
 */
public final class LogDirectoryConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LogDirectoryConfig.class);

    /** Environment variable names. */
    public static final String ENV_SERVER = "JBOSS_SERVER_LOG_DIR";
    public static final String ENV_APPLICATION = "JBOSS_APP_LOG_DIR";

    /** System-property fallback names. */
    public static final String PROP_SERVER = "jboss.server.log.dir";
    public static final String PROP_APPLICATION = "app.log.dir";

    private final Map<LogSet, Path> roots = new EnumMap<>(LogSet.class);

    /**
     * Builds a config from already-chosen directory paths. The application path
     * may be {@code null}, in which case it falls back to the server path. Each
     * path is canonicalized (normalized; resolved to its real path when it
     * exists).
     *
     * @param serverDir      the server log directory (required)
     * @param applicationDir the application log directory, or {@code null} to
     *                       reuse the server directory
     */
    public LogDirectoryConfig(Path serverDir, Path applicationDir) {
        Objects.requireNonNull(serverDir, "serverDir");
        Path server = canonicalize(serverDir);
        Path application = canonicalize(applicationDir != null ? applicationDir : serverDir);
        roots.put(LogSet.SERVER, server);
        roots.put(LogSet.APPLICATION, application);
    }

    /**
     * Resolves both roots from the supplied environment and system-property
     * lookups, applying the env &rarr; sysprop &rarr; default precedence.
     *
     * @param env        lookup for environment variables (e.g. {@code System::getenv})
     * @param systemProp lookup for system properties (e.g. {@code System::getProperty})
     * @return a fully resolved configuration
     */
    public static LogDirectoryConfig resolve(Function<String, String> env,
                                             Function<String, String> systemProp) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(systemProp, "systemProp");

        String server = firstNonBlank(env.apply(ENV_SERVER), systemProp.apply(PROP_SERVER));
        String application = firstNonBlank(env.apply(ENV_APPLICATION), systemProp.apply(PROP_APPLICATION));

        if (server == null) {
            LOG.warn("No server log directory configured ({} / {} unset); "
                    + "the server log tree will be empty.", ENV_SERVER, PROP_SERVER);
        }

        Path serverPath = server != null ? Path.of(server) : Path.of("");
        Path applicationPath = application != null ? Path.of(application) : null;
        return new LogDirectoryConfig(serverPath, applicationPath);
    }

    /**
     * Convenience factory that reads the real process environment and system
     * properties. Used by {@link LogConfigListener}.
     */
    public static LogDirectoryConfig fromEnvironment() {
        return resolve(System::getenv, System::getProperty);
    }

    /** @return the canonical root directory for the given log set. */
    public Path rootFor(LogSet set) {
        return roots.get(Objects.requireNonNull(set, "set"));
    }

    /** @return {@code true} if the root for the given set is a readable directory. */
    public boolean isUsable(LogSet set) {
        Path root = rootFor(set);
        return root != null && Files.isDirectory(root) && Files.isReadable(root);
    }

    /** Logs the resolved roots and their usability. Called once at startup. */
    public void logResolvedRoots() {
        for (LogSet set : LogSet.values()) {
            Path root = rootFor(set);
            if (isUsable(set)) {
                LOG.info("{} log directory resolved to {}", set, root);
            } else {
                LOG.warn("{} log directory {} is missing or not a readable directory; "
                        + "its tree will be empty.", set, root);
            }
        }
    }

    private static Path canonicalize(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            // Resolve symlinks / real casing when the directory actually exists.
            return normalized.toRealPath();
        } catch (Exception e) {
            // Directory may not exist yet; fall back to the normalized absolute path.
            return normalized;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
