package org.jboss.logviewer.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jboss.logviewer.config.LogDirectoryConfig;
import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.model.LogNode;
import org.jboss.logviewer.model.TailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists the filtered, sorted log directory tree for a {@link LogSet}.
 *
 * <p>Config is injected via the constructor so the service is unit-testable
 * against a {@code @TempDir} with no container. The tree is filtered to allowed
 * log/archive types (see {@link LogExtensions}); empty directories are pruned;
 * directories sort before files, alphabetically within each group.
 */
public class LogFileService {

    private static final Logger LOG = LoggerFactory.getLogger(LogFileService.class);

    /** Default maximum directory depth walked from the root. */
    public static final int DEFAULT_MAX_DEPTH = 10;

    /** Default tail-window size (bytes) returned on an initial read. */
    public static final int DEFAULT_TAIL_BYTES = 256 * 1024;

    private final LogDirectoryConfig config;
    private final PathSecurity pathSecurity;
    private final LogCodecService codecService;
    private final int maxDepth;

    public LogFileService(LogDirectoryConfig config) {
        this(config, new PathSecurity(), DEFAULT_MAX_DEPTH);
    }

    public LogFileService(LogDirectoryConfig config, int maxDepth) {
        this(config, new PathSecurity(), maxDepth);
    }

    public LogFileService(LogDirectoryConfig config, PathSecurity pathSecurity, int maxDepth) {
        this.config = config;
        this.pathSecurity = pathSecurity;
        this.codecService = new LogCodecService(config, pathSecurity);
        this.maxDepth = maxDepth;
    }

    /**
     * Builds the filtered directory tree for the given set. Returns a directory
     * node representing the root (possibly with no children). If the root is
     * missing or unreadable, returns an empty root node rather than throwing.
     */
    public LogNode listTree(LogSet set) {
        Path root = config.rootFor(set);
        if (root == null || !config.isUsable(set)) {
            LOG.debug("Root for {} is unusable; returning empty tree", set);
            return LogNode.directory("", "", List.of());
        }
        List<LogNode> children = listChildren(root, root, 1);
        return LogNode.directory("", "", children);
    }

    /**
     * Reads a window of a log file.
     *
     * <p>Plain (seekable) files use a byte-offset cursor:
     * <ul>
     *   <li>{@code fromOffset < 0} (initial load): returns the last
     *       {@code maxBytes} bytes (the tail window).</li>
     *   <li>{@code fromOffset >= 0} (poll): returns only the bytes appended since
     *       {@code fromOffset}.</li>
     *   <li>If the current size is smaller than {@code fromOffset}, rotation/
     *       truncation is assumed: {@code truncated=true} and the tail is
     *       returned for a full reload.</li>
     * </ul>
     *
     * <p>Compressed files delegate to {@link LogCodecService}: the offset cursor
     * does not apply (rotated archives don't grow), so a decompressed tail window
     * is returned with {@code compressed=true}.
     *
     * @param entryName archive entry to open (compressed multi-entry archives only)
     */
    public TailResult readTail(LogSet set, String relativePath, long fromOffset,
                               int maxBytes, String entryName) {
        Path root = config.rootFor(set);
        Path file = pathSecurity.resolve(root, relativePath);

        if (LogExtensions.isCompressed(file.getFileName().toString())) {
            return codecService.decompressTail(set, relativePath, entryName, maxBytes);
        }
        return readPlainTail(file, fromOffset, maxBytes);
    }

    /** Convenience overload without an archive entry name. */
    public TailResult readTail(LogSet set, String relativePath, long fromOffset, int maxBytes) {
        return readTail(set, relativePath, fromOffset, maxBytes, null);
    }

    private TailResult readPlainTail(Path file, long fromOffset, int maxBytes) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();

            boolean truncated = false;
            long start;
            if (fromOffset < 0) {
                // Initial load: last maxBytes bytes.
                start = Math.max(0, size - maxBytes);
            } else if (fromOffset > size) {
                // File shrank since last poll → rotation/truncation; reload tail.
                truncated = true;
                start = Math.max(0, size - maxBytes);
            } else {
                start = fromOffset;
            }

            int toRead = (int) Math.min((long) maxBytes, size - start);
            byte[] buffer = new byte[Math.max(0, toRead)];
            if (toRead > 0) {
                raf.seek(start);
                raf.readFully(buffer);
            }
            String content = new String(buffer, StandardCharsets.UTF_8);
            return new TailResult(content, size, size, truncated, false);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    /** Lists selectable entries of a multi-entry archive (delegates to the codec). */
    public List<org.jboss.logviewer.model.ArchiveEntry> listEntries(LogSet set, String relativePath) {
        return codecService.listEntries(set, relativePath);
    }

    /**
     * Lists the (filtered, sorted) children of {@code dir}, recursing into
     * subdirectories up to {@link #maxDepth}. Directories with no surviving
     * children after filtering are pruned.
     */
    private List<LogNode> listChildren(Path root, Path dir, int depth) {
        List<LogNode> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (depth >= maxDepth) {
                        continue; // depth bound reached; do not descend further
                    }
                    List<LogNode> grandChildren = listChildren(root, entry, depth + 1);
                    if (!grandChildren.isEmpty()) { // prune empty directories
                        result.add(LogNode.directory(
                                entry.getFileName().toString(),
                                relativePath(root, entry),
                                grandChildren));
                    }
                } else if (Files.isRegularFile(entry)) {
                    String name = entry.getFileName().toString();
                    if (!LogExtensions.isAllowed(name)) {
                        continue; // hide non-log/archive files (including .lck)
                    }
                    result.add(toFileNode(root, entry, name));
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to list directory {}: {}", dir, e.toString());
        }
        result.sort(NODE_ORDER);
        return result;
    }

    private LogNode toFileNode(Path root, Path file, String name) {
        long size = 0L;
        long modified = 0L;
        try {
            size = Files.size(file);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            LOG.debug("Could not stat {}: {}", file, e.toString());
        }
        return LogNode.file(name, relativePath(root, file), size, modified,
                LogExtensions.isCompressed(name));
    }

    private static String relativePath(Path root, Path entry) {
        // Always use forward slashes so the value is a stable web/relative path.
        return root.relativize(entry).toString().replace('\\', '/');
    }

    /** Directories first, then files; alphabetical (case-insensitive) within each group. */
    private static final Comparator<LogNode> NODE_ORDER =
            Comparator.comparing((LogNode n) -> n.isDirectory() ? 0 : 1)
                    .thenComparing(LogNode::getName, String.CASE_INSENSITIVE_ORDER);
}
