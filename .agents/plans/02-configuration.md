# 02 — M2: Configuration Layer

> **Goal:** resolve and expose the two log root directories from backend JNDI bindings, in a
> container-free, unit-testable way. Environment variables remain the deployment source of truth;
> server startup maps them into JNDI.
>
> **Prerequisites:** M1 (API WAR module exists and deploys). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`, package `org.jboss.logviewer.config`)

### `LogSet` (enum)
- `SERVER`, `APPLICATION`. Maps the UI toggle to a configured root.

### `LogDirectoryConfig`
- Holds the two canonical root `Path`s.
- **Resolution per root: JNDI binding → default** (see `00-overview.md` table).
  - Server: `java:/comp/env/server-log-root` → `/var/local/jboss/eap/standalone/log`.
  - Application: `java:/comp/env/app-log-root` → `/var/logs/applogs`.
- Canonicalize each root once (`toRealPath`, or `toAbsolutePath().normalize()` if it may not yet
  exist) and cache.
- **Testability:** accept the raw values (or a small JNDI resolver function/map) via **constructor**
  so tests can drive resolution without a real naming provider. No static `ServletContext` access
  here.
- `rootFor(LogSet)` → canonical `Path`.
- If a root is missing/unreadable: record it, log a warning via SLF4J, and let `listTree` (M4)
  return empty for that set — do **not** throw.

### `LogConfigListener implements ServletContextListener`
- `@WebListener`. On `contextInitialized`: build the singleton `LogDirectoryConfig` from the two
  server JNDI bindings, log the resolved roots via SLF4J, store it as a `ServletContext` attribute
  for servlets (M6) to retrieve.

## Validate

`LogDirectoryConfigTest` (JUnit 5, `mvn test`):
- JNDI binding wins over default;
- server and application defaults are used when bindings are absent or blank;
- missing/unreadable directory is flagged (not an exception);
- canonicalization normalizes the path.

Deploy once and confirm the two resolved roots appear in `server.log` at startup.

## Gate

Unit tests green; startup log shows both resolved roots.

## Notes

- Keep resolution logic inside `LogDirectoryConfig` (testable), not in the listener.
- The listener is the only place that reads the server naming context.
- Docker startup is responsible for writing `JBOSS_SERVER_LOG_DIR` and `JBOSS_APP_LOG_DIR` into the
  standalone profile as simple JNDI string bindings before EAP starts.
