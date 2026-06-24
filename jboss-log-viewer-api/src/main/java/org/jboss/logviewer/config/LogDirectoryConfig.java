package org.jboss.logviewer.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and holds the two log root directories.
 *
 * <p>Resolution per root uses backend JNDI lookups, with defaults if a binding
 * is missing or blank:
 * <ul>
 *   <li>Server: {@code java:/comp/env/server-log-root} &rarr;
 *       {@code /var/local/jboss/eap/standalone/log}.</li>
 *   <li>Application: {@code java:/comp/env/app-log-root} &rarr;
 *       {@code /var/logs/applogs}.</li>
 * </ul>
 *
 * <p>The resolution logic is exposed through {@link #resolve(Function)} with an
 * injectable lookup so it can be unit-tested without a naming provider. The
 * {@code (Path, Path)} constructor lets tests build a config directly from temp
 * directories.
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

    /** JNDI names configured by the server. */
    public static final String JNDI_SERVER = "java:/comp/env/server-log-root";
    public static final String JNDI_APPLICATION = "java:/comp/env/app-log-root";

    /** Defaults used by the server-side JNDI binding and as backend fallback. */
    public static final String DEFAULT_SERVER = "/var/local/jboss/eap/standalone/log";
    public static final String DEFAULT_APPLICATION = "/var/logs/applogs";

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
     * Resolves both roots from the supplied JNDI lookup, falling back to the
     * documented defaults when a binding is absent or blank.
     *
     * @param jndiLookup lookup for JNDI names
     * @return a fully resolved configuration
     */
    public static LogDirectoryConfig resolve(Function<String, String> jndiLookup) {
        Objects.requireNonNull(jndiLookup, "jndiLookup");

        String server = valueOrDefault(jndiLookup.apply(JNDI_SERVER), DEFAULT_SERVER);
        String application = valueOrDefault(jndiLookup.apply(JNDI_APPLICATION), DEFAULT_APPLICATION);

        return new LogDirectoryConfig(Path.of(server), Path.of(application));
    }

    /**
     * Convenience factory that reads the configured server JNDI names. Used by
     * {@link LogConfigListener}.
     */
    public static LogDirectoryConfig fromJndi() {
        return resolve(LogDirectoryConfig::lookupString);
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

    private static String valueOrDefault(String value, String defaultValue) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }

    private static String lookupString(String name) {
        try {
            Object value = new InitialContext().lookup(name);
            if (value instanceof String stringValue) {
                return stringValue;
            }
            if (value != null) {
                LOG.warn("JNDI binding {} has unsupported value type {}; using default.",
                        name, value.getClass().getName());
            }
        } catch (NamingException e) {
            LOG.warn("JNDI binding {} is not available; using default.", name);
        }
        return null;
    }
}
