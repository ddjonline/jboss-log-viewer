package org.jboss.logviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jboss.logviewer.config.LogDirectoryConfig;
import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.model.ArchiveEntry;
import org.jboss.logviewer.model.TailResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LogCodecService} — decompression round-trips, entry
 * listing, and the bounded tail-window reader. Fixtures are created in-test.
 */
class LogCodecServiceTest {

    private static final int WINDOW = 256 * 1024;

    private static LogCodecService codec(Path root) {
        return new LogCodecService(new LogDirectoryConfig(root, root), new PathSecurity());
    }

    @Test
    void roundTripGzip(@TempDir Path root) throws Exception {
        String text = "line one\nline two\nline three\n";
        Path gz = root.resolve("server.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }

        TailResult result = codec(root).decompressTail(LogSet.SERVER, "server.log.gz", null, WINDOW);

        assertTrue(result.compressed());
        assertEquals(text, result.content());
    }

    @Test
    void roundTripSingleEntryZip(@TempDir Path root) throws Exception {
        String text = "zipped log content\n";
        Path zip = root.resolve("logs.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("app.log"));
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        // Single entry → no entry name required.
        TailResult result = codec(root).decompressTail(LogSet.SERVER, "logs.zip", null, WINDOW);
        assertEquals(text, result.content());
    }

    @Test
    void roundTripTarGz(@TempDir Path root) throws Exception {
        String text = "tarball log line\n";
        Path tgz = root.resolve("logs.tar.gz");
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(Files.newOutputStream(tgz)))) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry("server.log");
            entry.setSize(bytes.length);
            out.putArchiveEntry(entry);
            out.write(bytes);
            out.closeArchiveEntry();
        }

        TailResult result = codec(root).decompressTail(LogSet.SERVER, "logs.tar.gz", "server.log", WINDOW);
        assertEquals(text, result.content());
    }

    @Test
    void listEntriesForMultiEntryZip(@TempDir Path root) throws Exception {
        Path zip = root.resolve("multi.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : List.of("a.log", "b.log", "c.log")) {
                out.putNextEntry(new ZipEntry(name));
                out.write(("content of " + name).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        List<ArchiveEntry> entries = codec(root).listEntries(LogSet.SERVER, "multi.zip");
        List<String> names = entries.stream().map(ArchiveEntry::name).collect(Collectors.toList());

        assertTrue(names.containsAll(List.of("a.log", "b.log", "c.log")));
        assertEquals(3, names.size());
    }

    @Test
    void listEntriesForTarGz(@TempDir Path root) throws Exception {
        Path tgz = root.resolve("multi.tar.gz");
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(Files.newOutputStream(tgz)))) {
            for (String name : List.of("one.log", "two.log")) {
                byte[] bytes = ("data " + name).getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(bytes.length);
                out.putArchiveEntry(entry);
                out.write(bytes);
                out.closeArchiveEntry();
            }
        }

        List<ArchiveEntry> entries = codec(root).listEntries(LogSet.SERVER, "multi.tar.gz");
        List<String> names = entries.stream().map(ArchiveEntry::name).collect(Collectors.toList());

        assertTrue(names.containsAll(List.of("one.log", "two.log")));
        assertEquals(2, names.size());
    }

    @Test
    void tailWindowBoundsLargeDecompressedStream() throws Exception {
        // 1 MB of data, 64 KB window → reader must retain exactly the last 64 KB.
        int window = 64 * 1024;
        int totalSize = 1024 * 1024;
        byte[] data = new byte[totalSize];
        for (int i = 0; i < totalSize; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }

        String result = LogCodecService.readTailWindow(new ByteArrayInputStream(data), window);

        assertEquals(window, result.getBytes(StandardCharsets.UTF_8).length,
                "retained content must equal the window size");
        // The retained tail must match the last `window` bytes of the source.
        String expectedTail = new String(data, totalSize - window, window, StandardCharsets.UTF_8);
        assertEquals(expectedTail, result);
    }

    @Test
    void tailWindowReturnsWholeStreamWhenSmaller() throws Exception {
        String text = "small content";
        String result = LogCodecService.readTailWindow(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), 4096);
        assertEquals(text, result);
    }
}
