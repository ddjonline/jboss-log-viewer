package org.jboss.logviewer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the log directory tree: either a directory (with children) or a
 * file. Pure data carrier produced by the file-listing service and serialized
 * to JSON by the API layer (M6).
 */
public final class LogNode {

    /** Node category. */
    public enum Type {
        DIRECTORY,
        FILE
    }

    private final String name;
    private final String relativePath;
    private final Type type;
    private final long size;
    private final long lastModified;
    private final boolean compressed;
    private final List<LogNode> children;

    private LogNode(String name, String relativePath, Type type, long size,
                    long lastModified, boolean compressed, List<LogNode> children) {
        this.name = name;
        this.relativePath = relativePath;
        this.type = type;
        this.size = size;
        this.lastModified = lastModified;
        this.compressed = compressed;
        this.children = children;
    }

    /** Creates a file node. */
    public static LogNode file(String name, String relativePath, long size,
                               long lastModified, boolean compressed) {
        return new LogNode(name, relativePath, Type.FILE, size, lastModified, compressed, null);
    }

    /** Creates a directory node with the given children. */
    public static LogNode directory(String name, String relativePath, List<LogNode> children) {
        return new LogNode(name, relativePath, Type.DIRECTORY, 0L, 0L, false,
                new ArrayList<>(children));
    }

    public String getName() {
        return name;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public Type getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public boolean isDirectory() {
        return type == Type.DIRECTORY;
    }

    /** @return children for a directory node, or {@code null} for a file node. */
    public List<LogNode> getChildren() {
        return children;
    }
}
