# 05 — M5: Tail Reading + Decompression Service

> **Goal:** read the tail of plain logs with an incremental offset cursor, and transparently
> decompress archived logs — the full read path, validated with zero web/UI involvement.
>
> **Prerequisites:** M2 (config), M3 (`PathSecurity`), M4 (`LogNode`). Read
> [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`)

### `model/TailResult`
Fields: `content` (String), `nextOffset` (long), `fileSize` (long), `truncated` (boolean),
`compressed` (boolean).

### `service/LogFileService.readTail(LogSet, relPath, fromOffset, maxBytes)`
For **plain/seekable** files (`.log`):
- Resolve+validate the path via `PathSecurity`.
- `fromOffset < 0` (initial load): seek to `max(0, size - maxBytes)`; return the tail window
  (default 256 KB).
- `fromOffset >= 0` (auto-refresh poll): return only bytes appended since `fromOffset`.
- Detect rotation/truncation: if current size < `fromOffset`, set `truncated = true` and signal a
  full reload (reset from the tail).
- Use `RandomAccessFile`/`FileChannel`. Read as UTF-8. Return `nextOffset = new file size`.
- `compressed = false`.

### `service/LogCodecService` (decompression)
Selects a reader by extension:

| Extension | Reader | Library |
|---|---|---|
| `.gz`, `.gzip` | `java.util.zip.GZIPInputStream` | base Java |
| `.zip` | `java.util.zip.ZipFile` (pick an entry) | base Java |
| `.tar.gz`, `.tgz` | `GzipCompressorInputStream` + `TarArchiveInputStream` | Apache Commons Compress |

- **Tail window for compressed files:** stream the decompressed bytes through a **bounded
  in-memory ring buffer** retaining only the last *N* bytes (default 256 KB, same cap). Never
  fully materialize the stream beyond the window. Returns `compressed = true`.
- `listEntries(LogSet, relPath)` → list of `{name, size}` for multi-entry archives (`.zip`, TAR);
  filter out directory and zero-byte entries.
- `readTail` delegates here for compressed paths: seekable → offset cursor; compressed →
  decompress (optionally a specific archive entry) + tail window. Auto-refresh does not apply to
  compressed files (rotated archives don't grow), so the offset cursor is not used for them.
- All paths validated through `PathSecurity`. Add the **Commons Compress** dependency to the API
  module POM in this milestone.

## Validate

Extend `LogFileServiceTest` and add `LogCodecServiceTest` (JUnit 5, `@TempDir`, fixtures created
in-test):
- plain file: initial tail window; incremental append returns only new bytes; truncation/rotation
  detected (`truncated = true`);
- round-trip decompress of `.gz`, `.zip`, `.tar.gz` fixtures returns the original text;
- `listEntries` returns the expected entries for a multi-entry `.zip` and a `.tar.gz`;
- tail-window bound holds on a large decompressed stream (memory stays within the cap).

## Gate

Unit tests green. The complete file-read + decompression path works with no servlet or browser.

## Notes

- Single-entry archives: the codec opens the sole entry directly; the picker (M7) is only for
  multi-entry archives.
- Keep `LogCodecService` constructor-injected and container-free for testing.
