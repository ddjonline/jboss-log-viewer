package org.jboss.logviewer.service;

import java.util.Locale;

/**
 * Central classification of the file types the viewer handles, by extension
 * (case-insensitive). Shared by the file-listing filter (M4) and the codec
 * selection logic (M5) so the rules stay in one place.
 *
 * <p>Allowed types: {@code .log}, {@code .gz}, {@code .gzip}, {@code .tar.gz},
 * {@code .tgz}, {@code .zip}. Everything else (including {@code .lck}) is hidden.
 */
public final class LogExtensions {

    /** How a file should be read. */
    public enum Kind {
        /** Plain text log, seekable, supports incremental tailing. */
        PLAIN,
        /** Single-stream gzip ({@code .gz}, {@code .gzip}). */
        GZIP,
        /** Zip archive ({@code .zip}); may hold multiple entries. */
        ZIP,
        /** Gzipped TAR archive ({@code .tar.gz}, {@code .tgz}); may hold multiple entries. */
        TAR_GZ
    }

    private LogExtensions() {
    }

    /**
     * Classifies a file name, or returns {@code null} if it is not an allowed
     * type and should be hidden from the tree.
     *
     * <p>{@code .tar.gz}/{@code .tgz} are matched before the single {@code .gz}
     * rule so a tarball is classified as {@link Kind#TAR_GZ}, not {@link Kind#GZIP}.
     */
    public static Kind classify(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return Kind.TAR_GZ;
        }
        if (name.endsWith(".zip")) {
            return Kind.ZIP;
        }
        if (name.endsWith(".gz") || name.endsWith(".gzip")) {
            return Kind.GZIP;
        }
        if (name.endsWith(".log")) {
            return Kind.PLAIN;
        }
        return null;
    }

    /** @return {@code true} if the file is an allowed (viewable) type. */
    public static boolean isAllowed(String fileName) {
        return classify(fileName) != null;
    }

    /** @return {@code true} if the file is a compressed archive (not a plain log). */
    public static boolean isCompressed(String fileName) {
        Kind kind = classify(fileName);
        return kind != null && kind != Kind.PLAIN;
    }
}
