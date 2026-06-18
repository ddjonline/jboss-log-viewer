# 04 — M4: File Listing Service

> **Goal:** build the filtered, sorted directory tree the UI renders on the left.
>
> **Prerequisites:** M2 (config), M3 (`PathSecurity`). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`)

### `model/LogNode`
Carries per node:
- `name` (display), `relativePath` (from the root),
- `type` (`DIRECTORY` | `FILE`), `size` (bytes), `lastModified` (epoch millis),
- `compressed` (true for `.gz/.gzip/.tar.gz/.tgz/.zip`),
- `children` (for directories).

### `service/LogFileService.listTree(LogSet)`
- Resolve the root via `LogDirectoryConfig.rootFor(set)`. If the root is missing/unreadable,
  return an empty tree (no exception).
- Walk the tree with a **bounded depth** (default max 10).
- **File filter:** include only `*.log`, `*.gz`, `*.gzip`, `*.tar.gz`, `*.tgz`, `*.zip`
  (case-insensitive extension match). Hide everything else, including `*.lck`.
- **Prune** directories that are empty after filtering.
- **Sort:** directories first, then files; each group alphabetical by name.
- Mark archive files `compressed = true`.
- Inject config via **constructor** (testable; no `ServletContext` here).

## Validate

`LogFileServiceTest` (JUnit 5, `@TempDir` fixture mirroring a real log dir):
- only allowed extensions appear; `.lck` and other files hidden;
- empty (or fully-filtered) directories are pruned;
- directories sort before files; alphabetical within each group;
- depth bound respected;
- `compressed` flag correct per extension;
- missing root → empty tree, no exception.

## Gate

Unit tests green.

## Notes

- Match `.tar.gz`/`.tgz` before the single-`.gz` rule so a `.tar.gz` is classified as a TAR
  archive, not a plain gzip (matters for M5).
- Tree building is pure: takes a root path, returns `LogNode`. No servlet/JSON concerns yet.
