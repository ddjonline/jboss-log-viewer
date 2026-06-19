package org.jboss.logviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logviewer.config.LogDirectoryConfig;
import org.jboss.logviewer.config.LogSet;
import org.jboss.logviewer.model.LogNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LogFileService#listTree} — filtering, pruning, sorting,
 * the {@code compressed} flag, depth bound, and missing-root handling.
 */
class LogFileServiceTest {

    private static LogFileService service(Path root) {
        return new LogFileService(new LogDirectoryConfig(root, root));
    }

    private static LogFileService service(Path root, int maxDepth) {
        return new LogFileService(new LogDirectoryConfig(root, root), maxDepth);
    }

    private static List<String> names(List<LogNode> nodes) {
        return nodes.stream().map(LogNode::getName).collect(Collectors.toList());
    }

    @Test
    void includesOnlyAllowedExtensionsAndHidesOthers(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("server.log"), "x");
        Files.writeString(root.resolve("rotated.log.gz"), "x");
        Files.writeString(root.resolve("archive.zip"), "x");
        Files.writeString(root.resolve("bundle.tar.gz"), "x");
        Files.writeString(root.resolve("server.log.lck"), "x"); // hidden
        Files.writeString(root.resolve("notes.txt"), "x");      // hidden
        Files.writeString(root.resolve("data.bin"), "x");       // hidden

        List<LogNode> children = service(root).listTree(LogSet.SERVER).getChildren();
        List<String> shown = names(children);

        assertTrue(shown.contains("server.log"));
        assertTrue(shown.contains("rotated.log.gz"));
        assertTrue(shown.contains("archive.zip"));
        assertTrue(shown.contains("bundle.tar.gz"));
        assertFalse(shown.contains("server.log.lck"), ".lck must be hidden");
        assertFalse(shown.contains("notes.txt"), ".txt must be hidden");
        assertFalse(shown.contains("data.bin"), ".bin must be hidden");
    }

    @Test
    void compressedFlagSetCorrectly(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("plain.log"), "x");
        Files.writeString(root.resolve("rolled.gz"), "x");
        Files.writeString(root.resolve("bundle.tgz"), "x");

        List<LogNode> children = service(root).listTree(LogSet.SERVER).getChildren();

        for (LogNode node : children) {
            switch (node.getName()) {
                case "plain.log" -> assertFalse(node.isCompressed());
                case "rolled.gz", "bundle.tgz" -> assertTrue(node.isCompressed());
                default -> { /* none */ }
            }
        }
    }

    @Test
    void emptyAndFullyFilteredDirectoriesArePruned(@TempDir Path root) throws Exception {
        Files.createDirectory(root.resolve("empty"));
        Path noLogs = Files.createDirectory(root.resolve("nologs"));
        Files.writeString(noLogs.resolve("readme.txt"), "x"); // filtered out → dir pruned
        Path withLog = Files.createDirectory(root.resolve("withlog"));
        Files.writeString(withLog.resolve("app.log"), "x");

        List<LogNode> children = service(root).listTree(LogSet.SERVER).getChildren();
        List<String> shown = names(children);

        assertFalse(shown.contains("empty"), "empty dir must be pruned");
        assertFalse(shown.contains("nologs"), "fully-filtered dir must be pruned");
        assertTrue(shown.contains("withlog"), "dir with a log must remain");
    }

    @Test
    void directoriesSortBeforeFilesAlphabetically(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("b.log"), "x");
        Files.writeString(root.resolve("a.log"), "x");
        Path zdir = Files.createDirectory(root.resolve("zdir"));
        Files.writeString(zdir.resolve("inner.log"), "x");
        Path mdir = Files.createDirectory(root.resolve("mdir"));
        Files.writeString(mdir.resolve("inner.log"), "x");

        List<LogNode> children = service(root).listTree(LogSet.SERVER).getChildren();

        // Directories (mdir, zdir) first in alpha order, then files (a.log, b.log).
        assertEquals(List.of("mdir", "zdir", "a.log", "b.log"), names(children));
    }

    @Test
    void depthBoundIsRespected(@TempDir Path root) throws Exception {
        // root/d1/d2/deep.log with maxDepth=2 must not surface deep.log's directory chain.
        Path d1 = Files.createDirectory(root.resolve("d1"));
        Path d2 = Files.createDirectory(d1.resolve("d2"));
        Files.writeString(d2.resolve("deep.log"), "x");
        Files.writeString(d1.resolve("shallow.log"), "x");

        // maxDepth=2: depth 1 = root's children, depth 2 = d1's children. d1 is at
        // depth 1; listing its children (depth 2) is allowed, but it will not descend
        // into d2 (which would be depth 3). So shallow.log shows, deep.log's dir is pruned.
        List<LogNode> children = service(root, 2).listTree(LogSet.SERVER).getChildren();

        LogNode d1Node = children.stream()
                .filter(n -> n.getName().equals("d1")).findFirst().orElseThrow();
        List<String> d1Children = names(d1Node.getChildren());
        assertTrue(d1Children.contains("shallow.log"));
        assertFalse(d1Children.contains("d2"), "d2 beyond the depth bound must be pruned");
    }

    @Test
    void missingRootReturnsEmptyTreeWithoutThrowing(@TempDir Path base) {
        Path missing = base.resolve("does-not-exist");
        LogFileService svc = new LogFileService(new LogDirectoryConfig(missing, missing));

        LogNode tree = svc.listTree(LogSet.SERVER);

        assertTrue(tree.getChildren().isEmpty(), "missing root must yield an empty tree");
    }

    // ---- readTail (M5) ------------------------------------------------------

    @Test
    void initialLoadReturnsTailWindow(@TempDir Path root) throws Exception {
        // 10 KB file, 4 KB window → only the last 4 KB returned.
        byte[] data = new byte[10_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ('a' + (i % 26));
        }
        Files.write(root.resolve("big.log"), data);

        var result = service(root).readTail(LogSet.SERVER, "big.log", -1, 4096);

        assertFalse(result.compressed());
        assertFalse(result.truncated());
        assertEquals(10_000, result.fileSize());
        assertEquals(10_000, result.nextOffset());
        assertEquals(4096, result.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void incrementalPollReturnsOnlyAppendedBytes(@TempDir Path root) throws Exception {
        Path log = root.resolve("app.log");
        Files.writeString(log, "first\n");

        var first = service(root).readTail(LogSet.SERVER, "app.log", -1, 4096);
        long offset = first.nextOffset();

        Files.writeString(log, "second\n", java.nio.file.StandardOpenOption.APPEND);
        var second = service(root).readTail(LogSet.SERVER, "app.log", offset, 4096);

        assertEquals("second\n", second.content(), "poll must return only the appended bytes");
        assertFalse(second.truncated());
    }

    @Test
    void truncationIsDetected(@TempDir Path root) throws Exception {
        Path log = root.resolve("app.log");
        Files.writeString(log, "a lot of original content\n");

        var first = service(root).readTail(LogSet.SERVER, "app.log", -1, 4096);
        long offset = first.nextOffset();

        // Rotate: file shrinks below the previous offset.
        Files.writeString(log, "x\n");
        var after = service(root).readTail(LogSet.SERVER, "app.log", offset, 4096);

        assertTrue(after.truncated(), "a shrunken file must be reported as truncated");
        assertEquals("x\n", after.content());
    }
}
