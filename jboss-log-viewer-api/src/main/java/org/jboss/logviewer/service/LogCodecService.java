package org.jboss.logviewer.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jboss.logviewer.config.LogDirectoryConfig;
import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.model.ArchiveEntry;
import org.jboss.logviewer.model.TailResult;

/**
 * Transparently decompresses archived logs ({@code .gz}, {@code .gzip},
 * {@code .zip}, {@code .tar.gz}, {@code .tgz}) and exposes their text.
 *
 * <p>Because compressed streams are not randomly seekable, only a bounded
 * <em>tail window</em> (the last N bytes of the decompressed content) is
 * retained in memory via a ring buffer, so even a huge compressed log never
 * exhausts the heap. Auto-refresh does not apply to compressed files (rotated
 * archives don't grow), so no resumable offset cursor is used.
 *
 * <p>Constructor-injected and container-free for unit testing.
 */
public class LogCodecService {

    private final LogDirectoryConfig config;
    private final PathSecurity pathSecurity;

    public LogCodecService(LogDirectoryConfig config, PathSecurity pathSecurity) {
        this.config = config;
        this.pathSecurity = pathSecurity;
    }

    /**
     * Lists the selectable entries inside a multi-entry archive ({@code .zip} or
     * TAR). Directory entries and zero-byte entries are filtered out. For a
     * single-stream gzip ({@code .gz}/{@code .gzip}) there are no named entries,
     * so an empty list is returned.
     */
    public List<ArchiveEntry> listEntries(LogSet set, String relativePath) {
        Path file = pathSecurity.resolve(config.rootFor(set), relativePath);
        LogExtensions.Kind kind = LogExtensions.classify(file.getFileName().toString());
        if (kind == null) {
            throw new IllegalArgumentException("Unsupported file type: " + relativePath);
        }
        try {
            return switch (kind) {
                case ZIP -> listZipEntries(file);
                case TAR_GZ -> listTarEntries(file);
                case GZIP, PLAIN -> List.of();
            };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read archive entries from " + relativePath, e);
        }
    }

    /**
     * Decompresses {@code relativePath} and returns the tail window of its text.
     *
     * @param entryName for multi-entry archives, the entry to open; ignored for
     *                  gzip; for archives left {@code null}/blank the sole entry
     *                  is opened (and it is an error if there are several)
     * @param maxBytes  tail-window cap in bytes
     */
    public TailResult decompressTail(LogSet set, String relativePath, String entryName, int maxBytes) {
        Path file = pathSecurity.resolve(config.rootFor(set), relativePath);
        LogExtensions.Kind kind = LogExtensions.classify(file.getFileName().toString());
        if (kind == null || kind == LogExtensions.Kind.PLAIN) {
            throw new IllegalArgumentException("Not a compressed file: " + relativePath);
        }
        long fileSize = sizeOf(file);
        try (InputStream in = openDecompressed(file, kind, entryName)) {
            String content = readTailWindow(in, maxBytes);
            return new TailResult(content, fileSize, fileSize, false, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress " + relativePath, e);
        }
    }

    // ---- decompressed stream selection -------------------------------------

    private InputStream openDecompressed(Path file, LogExtensions.Kind kind, String entryName)
            throws IOException {
        return switch (kind) {
            case GZIP -> new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)));
            case ZIP -> openZipEntry(file, entryName);
            case TAR_GZ -> openTarEntry(file, entryName);
            case PLAIN -> throw new IllegalArgumentException("Not compressed");
        };
    }

    private InputStream openZipEntry(Path file, String entryName) throws IOException {
        ZipFile zip = new ZipFile(file.toFile());
        ZipEntry target = selectZipEntry(zip, entryName);
        // Wrap so closing the returned stream also closes the ZipFile.
        InputStream raw = zip.getInputStream(target);
        return new java.io.FilterInputStream(raw) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }

    private ZipEntry selectZipEntry(ZipFile zip, String entryName) throws IOException {
        if (entryName != null && !entryName.isBlank()) {
            ZipEntry e = zip.getEntry(entryName);
            if (e == null || e.isDirectory()) {
                zip.close();
                throw new IllegalArgumentException("No such entry in archive: " + entryName);
            }
            return e;
        }
        ZipEntry sole = null;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory() || e.getSize() == 0) {
                continue;
            }
            if (sole != null) {
                zip.close();
                throw new IllegalArgumentException(
                        "Archive has multiple entries; an entry name is required");
            }
            sole = e;
        }
        if (sole == null) {
            zip.close();
            throw new IllegalArgumentException("Archive has no readable entries");
        }
        return sole;
    }

    private InputStream openTarEntry(Path file, String entryName) throws IOException {
        TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(file))));
        TarArchiveEntry entry;
        TarArchiveEntry chosen = null;
        boolean wantNamed = entryName != null && !entryName.isBlank();
        while ((entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory() || entry.getSize() == 0) {
                continue;
            }
            if (wantNamed) {
                if (entry.getName().equals(entryName)) {
                    return tar; // positioned at the requested entry
                }
            } else if (chosen == null) {
                // Sole-entry case: the stream is now positioned at the first file
                // entry. We cannot rewind, so return immediately and require an
                // explicit name when multiple entries exist.
                return tar;
            }
        }
        tar.close();
        throw new IllegalArgumentException(wantNamed
                ? "No such entry in archive: " + entryName
                : "Archive has no readable entries");
    }

    // ---- entry listing ------------------------------------------------------

    private List<ArchiveEntry> listZipEntries(Path file) throws IOException {
        List<ArchiveEntry> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getSize() != 0) {
                    result.add(new ArchiveEntry(e.getName(), e.getSize()));
                }
            }
        }
        return result;
    }

    private List<ArchiveEntry> listTarEntries(Path file) throws IOException {
        List<ArchiveEntry> result = new ArrayList<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            TarArchiveEntry e;
            while ((e = tar.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getSize() != 0) {
                    result.add(new ArchiveEntry(e.getName(), e.getSize()));
                }
            }
        }
        return result;
    }

    // ---- bounded tail-window reader ----------------------------------------

    /**
     * Reads {@code in} to its end while retaining only the last {@code maxBytes}
     * bytes in a fixed-size ring buffer, then decodes them as UTF-8. Memory use
     * is bounded by {@code maxBytes} regardless of stream length.
     */
    static String readTailWindow(InputStream in, int maxBytes) throws IOException {
        byte[] ring = new byte[maxBytes];
        int writePos = 0;
        long total = 0;
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (read >= maxBytes) {
                // This chunk alone overflows the window: keep only its tail.
                System.arraycopy(chunk, read - maxBytes, ring, 0, maxBytes);
                writePos = 0; // ring is full, aligned at 0
            } else {
                for (int i = 0; i < read; i++) {
                    ring[writePos] = chunk[i];
                    writePos = (writePos + 1) % maxBytes;
                }
            }
        }

        int retained = (int) Math.min(total, maxBytes);
        byte[] ordered = new byte[retained];
        if (total <= maxBytes) {
            System.arraycopy(ring, 0, ordered, 0, retained);
        } else {
            // Ring is full; oldest byte is at writePos.
            int firstLen = maxBytes - writePos;
            System.arraycopy(ring, writePos, ordered, 0, firstLen);
            System.arraycopy(ring, 0, ordered, firstLen, writePos);
        }
        return new String(ordered, StandardCharsets.UTF_8);
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1L;
        }
    }
}
