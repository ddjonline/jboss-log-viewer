package org.jboss.logviewer.model;

/**
 * A selectable file entry within a multi-entry archive ({@code .zip} or TAR).
 *
 * @param name uncompressed entry path within the archive
 * @param size uncompressed size in bytes ({@code -1} if unknown)
 */
public record ArchiveEntry(String name, long size) {
}
