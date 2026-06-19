package org.jboss.logviewer.service;

/**
 * Thrown when a client-supplied path is rejected by {@link PathSecurity} — for
 * example an absolute path, a {@code ../} traversal attempt, or a symlink whose
 * target resolves outside the configured root.
 *
 * <p>Kept as a distinct type so the API layer (M6) can map it cleanly to an
 * HTTP {@code 403 Forbidden} response.
 */
public class PathSecurityException extends RuntimeException {

    public PathSecurityException(String message) {
        super(message);
    }

    public PathSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
