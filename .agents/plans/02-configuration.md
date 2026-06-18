# 02 — M2: Configuration Layer

> **Goal:** resolve and expose the two log root directories from environment properties, in a
> container-free, unit-testable way.
>
> **Prerequisites:** M1 (API WAR module exists and deploys). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`, package `org.jboss.logviewer.config`)

### `LogSet` (enum)
- `SERVER`, `APPLICATION`. Maps the UI toggle to a configured root.

### `LogDirectoryConfig`
- Holds the two canonical root `Path`s.
- **Resolution per root: env var → system property → default** (see `00-overview.md` table).
  - Server: `JBOSS_SERVER_LOG_DIR` → `jboss.server.log.dir` → `EAP_HOME/standalone/log`.
  - Application: `JBOSS_APP_LOG_DIR` → `app.log.dir` → falls back to the server root.
- Canonicalize each root once (`toRealPath`, or `toAbsolutePath().normalize()` if it may not yet
  exist) and cache.
- **Testability:** accept the raw values (or a small resolver function/map) via **constructor**
  so tests can drive resolution without real env vars. No static `ServletContext` access here.
- `rootFor(LogSet)` → canonical `Path`.
- If a root is missing/unreadable: record it, log a warning via SLF4J, and let `listTree` (M4)
  return empty for that set — do **not** throw.

### `LogConfigListener implements ServletContextListener`
- `@WebListener`. On `contextInitialized`: build the singleton `LogDirectoryConfig` from real env
  vars/system properties, log the two resolved roots via SLF4J, store it as a `ServletContext`
  attribute for servlets (M6) to retrieve.

## Validate

`LogDirectoryConfigTest` (JUnit 5, `mvn test`):
- env var wins over system property wins over default (precedence);
- application root falls back to the server root when unset;
- missing/unreadable directory is flagged (not an exception);
- canonicalization normalizes the path.

Deploy once and confirm the two resolved roots appear in `server.log` at startup.

## Gate

Unit tests green; startup log shows both resolved roots.

## Notes

- Keep resolution logic inside `LogDirectoryConfig` (testable), not in the listener.
- The listener is the only place that reads the real process environment.
