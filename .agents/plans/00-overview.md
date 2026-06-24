# 00 — Project Overview & Shared Rules

> Read this first. Every milestone file (`01`–`08`) assumes the constraints here. The full
> design is in [`implementation-plan.md`](../../implementation-plan.md); these rule files are the
> ordered, independently-workable breakdown of it.

## What this is

A **Jakarta EE 10** web application that exposes **JBoss EAP 8+ / WildFly** server and
application log files through a browser UI: a file tree on the left, a live auto-scrolling text
view on the right, optional 5-second auto-refresh, and transparent decompression of archived logs.

## Module architecture (must preserve)

Maven multi-module reactor → **one deployable EAR** containing **two WARs**:

| Module | Packaging | Context root | Contents |
|---|---|---|---|
| `jboss-log-viewer-api` | war | `/jboss/logs/viewer/api` | all Java logic + servlets |
| `jboss-log-viewer-web` | war | `/jboss/logs/viewer` | static UI only (no Java) |
| `jboss-log-viewer-ear` | ear | — | bundles both WARs → `jboss-log-viewer.ear` |

- Routing uses the server's **longest-prefix context-root matching**:
  `/jboss/logs/viewer/api/tree` → API WAR; `/jboss/logs/viewer/index.html` → Web WAR.
- API servlet mappings are **short** (`/tree`, `/entries`, `/content`); the API context root
  supplies the prefix. Never hardcode the full path in `@WebServlet`.
- The frontend calls APIs **relative to the page** (`./api/tree`). Never hardcode host/context.

## Hard constraints (do not violate)

- **Java 21** (`maven.compiler.release=21`).
- **Base Java / Jakarta EE only — no frameworks.** No Spring, no JAX-RS, no CDI for logic.
  Endpoints are plain `HttpServlet`. JSON via **Jakarta JSON-P** (`jakarta.json.*`), not
  Jackson/Gson.
- **SLF4J only** for logging; `slf4j-api` is `provided` (EAP supplies the binding). Never bundle
  an SLF4J implementation.
- **Frontend is pure HTML/CSS/vanilla JS** (ES2021, `fetch`). No frameworks, no build step, no
  npm. Render log text with `textContent`, never `innerHTML`.
- **Industry-standard libraries only.** The one third-party runtime dependency is **Apache
  Commons Compress** (TAR support). Any new dependency must be justified in the plan first.
- **Read-only.** Lists and reads logs; no write/delete/move endpoints.

## Configuration

Two log roots are sourced from environment variables, written into server JNDI during startup, and
read by the API backend through JNDI lookups.

| Purpose | Source env var | Backend JNDI key | Default |
|---|---|---|---|
| Server logs | `JBOSS_SERVER_LOG_DIR` | `java:/comp/env/server-log-root` | `/var/local/jboss/eap/standalone/log` |
| Application logs | `JBOSS_APP_LOG_DIR` | `java:/comp/env/app-log-root` | `/var/logs/applogs` |

The Docker image startup configures these JNDI simple bindings in the standalone profile before
EAP starts. Direct non-Docker deployments must configure equivalent JNDI bindings.

Missing/unreadable root → log a warning (SLF4J) and serve an empty tree; never fail deployment.

## File filter (tree)

Show only: `*.log`, `*.gz`, `*.gzip`, `*.tar.gz`, `*.tgz`, `*.zip` (case-insensitive). Hide
everything else (including `*.lck`). Prune empty directories after filtering.

## Security rules (non-negotiable)

- **All client paths go through `PathSecurity`**: resolve against the root, canonicalize
  (`toRealPath`), reject unless still under the root. Blocks `../`, absolute paths, symlink
  escapes. Every file-accessing endpoint uses it.
- Cap reads with the **tail window** (default 256 KB). Never stream an unbounded file.

## Testability rule

Logic classes (`LogDirectoryConfig`, `PathSecurity`, `LogFileService`, `LogCodecService`) take
their root `Path`/config via **constructor** — no static `ServletContext` access inside them — so
they unit-test against a JUnit `@TempDir` with no container. Servlets read config from the
`ServletContext` and pass it in.

## How to work the milestones

- Do them **in order, M1 → M8**. Each must **compile, ship its own tests, and pass its validation
  gate** before the next starts.
- "Done" = `mvn test` green (logic layers) or the documented `curl`/browser smoke check passes
  (web layers).
- Match surrounding code style. Keep the two WARs decoupled (no shared runtime state).

## Build / test / deploy

```bash
mvn clean package   # → jboss-log-viewer-ear/target/jboss-log-viewer.ear
mvn test            # JUnit 5 unit tests (no container)
mvn clean verify    # full build + tests
```

- Deploy the **EAR** to `EAP_HOME/standalone/deployments/`.
- UI: `http://<host>:8080/jboss/logs/viewer/index.html`
- API: `http://<host>:8080/jboss/logs/viewer/api/{tree,entries,content}`

### Local container quickstart (WildFly)

For local runs without a full install, use the public WildFly image and volume-mount the built EAR.
Configure the same JNDI bindings documented above for equivalent log-root behavior; the simple
command below relies on backend defaults:

```bash
docker run -d --rm -p 8080:8080 \
  -v "$PWD/jboss-log-viewer-ear/target/jboss-log-viewer.ear:/opt/jboss/wildfly/standalone/deployments/jboss-log-viewer.ear:ro" \
  -v "$PWD/app-logs:/var/logs/applogs" \
  quay.io/wildfly/wildfly:latest-jdk21
```

Note: Red Hat EAP images from `registry.redhat.io` require authentication; anonymous pulls will fail with `401 Unauthorized`. Prefer the WildFly image above for local development unless you have valid registry credentials.

## Milestone index

| File | Milestone | Layer |
|---|---|---|
| `01-skeleton-ear-context-roots.md` | M1 | Multi-module skeleton + EAR + context roots |
| `02-configuration.md` | M2 | Configuration layer |
| `03-path-security.md` | M3 | Path-traversal security |
| `04-file-listing.md` | M4 | File listing service |
| `05-tail-and-decompression.md` | M5 | Tail reading + decompression |
| `06-api-servlets.md` | M6 | API servlets |
| `07-frontend.md` | M7 | Frontend (Web WAR) |
| `08-docs-and-final.md` | M8 | Documentation & final pass |
