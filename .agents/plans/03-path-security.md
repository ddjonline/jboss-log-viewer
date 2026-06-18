# 03 — M3: Path-Traversal Security

> **Goal:** the single most important control — confine every client-supplied path to the
> configured root. Built and proven before any file-reading endpoint exists.
>
> **Prerequisites:** M2 (`LogDirectoryConfig`, `LogSet`). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`, package `org.jboss.logviewer.service`)

### `PathSecurity`
- Constructed with (or given per call) the root `Path` for a `LogSet`.
- `resolve(Path root, String relativePath)` → canonical `Path`, or throws a dedicated
  `SecurityException`-style exception (caught by `ApiErrorHandler` in M6 → HTTP 403):
  1. Reject `null`/empty and any absolute input.
  2. Resolve the relative path against the root.
  3. Canonicalize with `toRealPath()` (follows symlinks to their real target).
  4. **Reject unless the canonical result still starts with the canonical root.**
- This blocks `../` traversal, absolute paths, and **symlink escapes** (a symlink whose target
  resolves outside the root is rejected because step 3 resolves it before the step-4 check).
- For paths that may not exist yet, normalize without `toRealPath` and still enforce the
  prefix check; reject on any normalization that climbs above the root.

## Validate

`PathSecurityTest` (JUnit 5, `@TempDir`):
- `../` / `..\\` traversal → rejected;
- absolute path (e.g. `/etc/passwd`) → rejected;
- a symlink inside the root pointing outside → rejected;
- valid nested relative path (e.g. `subdir/app.log`) → accepted and resolves under the root;
- the root itself / a direct child → accepted.

## Gate

Unit tests green. Security control proven with no endpoint and no UI involved.

## Notes

- Every filesystem-touching operation in M4/M5/M6 **must** route its client path through this
  class. No exceptions.
- Keep the rejection type distinct so M6 can map it cleanly to a `403`.
