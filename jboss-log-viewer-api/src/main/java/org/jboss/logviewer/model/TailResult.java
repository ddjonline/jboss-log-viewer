package org.jboss.logviewer.model;

/**
 * The result of reading (a window of) a log file.
 *
 * @param content    the decoded text returned to the client
 * @param nextOffset byte offset to poll from next time (plain files); for
 *                   compressed files this is the file size and is not used as a
 *                   resumable cursor
 * @param fileSize   current size of the underlying file in bytes
 * @param truncated  {@code true} if rotation/truncation was detected and the
 *                   client should perform a full reload
 * @param compressed {@code true} if the content was produced by decompressing an
 *                   archive (auto-refresh does not apply)
 * @param absolutePath absolute filesystem path of the underlying file
 */
public record TailResult(String content, long nextOffset, long fileSize,
                         boolean truncated, boolean compressed, String absolutePath) {
}
