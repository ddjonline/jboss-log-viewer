# AGENTS.md

Guidance for AI agents (and humans) working in this repository. Read this and
`implementation-plan.md` before making changes.

## What this project is

A **Jakarta EE 10** web application that exposes **JBoss EAP 8+ / WildFly** server and
application log files through a browser UI: a file tree on the left, a live auto-scrolling text
view on the right, with optional 5-second auto-refresh and transparent decompression of archived
logs.

`implementation-plan.md` is the source of truth for design and build order. If a change
contradicts the plan, update the plan in the same change and note why.

## Architecture (must preserve)

Maven multi-module reactor producing **one deployable EAR** containing **two WARs**:

| Module | Packaging | Context root | Contents |
|---|---|---|---|
| `jboss-log-viewer-api` | war | `/jboss/logs/viewer/api` | all Java logic + servlets |
| `jboss-log-viewer-web` | war | `/jboss/logs/viewer` | static UI only (no Java) |
| `jboss-log-viewer-ear` | ear | — | bundles both WARs → `jboss-log-viewer.ear` |

- Routing depends on the server's **longest-prefix context-root matching**:
  `/jboss/logs/viewer/api/tree` → API WAR; `/jboss/logs/viewer/index.html` → Web WAR.
- API servlet mappings are **short** (`/tree`, `/entries`, `/content`) — the API context root
  supplies the `/jboss/logs/viewer/api` prefix. Do not hardcode the full path in `@WebServlet`.
- The frontend calls APIs **relative to the page** (`./api/tree`). Never hardcode host or
  context root in `app.js`.

## Hard constraints (do not violate)

- **Java 21** (`maven.compiler.release=21`). Use modern Java; no preview features.
- **Base Java / Jakarta EE only — no frameworks.** No Spring, no JAX-RS, no CDI for business
  logic. Endpoints are plain `HttpServlet`. JSON is **Jakarta JSON-P** (`jakarta.json.*`) —
  **not** Jackson or Gson.
- **Logging via SLF4J only** (`org.slf4j`). `slf4j-api` is `provided` (EAP supplies the binding
  via `jboss-logging`). Never bundle an SLF4J implementation in the WAR/EAR.
- **Frontend is pure HTML/CSS/vanilla JS** (ES2021, `fetch`). No React/Vue/jQuery, **no build
  step**, no npm. Render log text with `textContent`, never `innerHTML` (XSS).
- **Industry-standard libraries only.** The single third-party runtime dependency is
  **Apache Commons Compress** (TAR support). Justify any new dependency in the plan first.
- **Read-only.** The app lists and reads logs. No write/delete/move endpoints, ever.

## Security (non-negotiable)

- **All client paths go through `PathSecurity`**: resolve against the configured root,
  canonicalize (`toRealPath`), and reject unless still under the root. This blocks `../`,
  absolute paths, and symlink escapes. Every file-accessing endpoint must use it.
- Cap reads with the **tail window** (default 256 KB) — never stream an unbounded file.
- No new endpoint may touch the filesystem without going through the service layer.

## Configuration

Two log roots are sourced from environment variables, written into server JNDI during startup, and
read by the API backend through JNDI lookups.

| Purpose | Source env var | Backend JNDI key | Default |
|---|---|---|---|
| Server logs | `JBOSS_SERVER_LOG_DIR` | `java:/comp/env/server-log-root` | `/var/local/jboss/eap/standalone/log` |
| Application logs | `JBOSS_APP_LOG_DIR` | `java:/comp/env/app-log-root` | `/var/logs/applogs` |

The Docker image startup configures these JNDI simple bindings in the standalone profile before
EAP starts. Direct non-Docker deployments must configure equivalent JNDI bindings.

## Testability rule

Logic classes (`LogDirectoryConfig`, `PathSecurity`, `LogFileService`, `LogCodecService`) take
their root `Path`/config via **constructor** — no static `ServletContext` access inside them —
so they are unit-testable against a JUnit `@TempDir` with no container. Servlets read the config
from the `ServletContext` and pass it in.

## Build, test, deploy

```bash
mvn clean package          # builds all modules → jboss-log-viewer-ear/target/jboss-log-viewer.ear
mvn test                   # JUnit 5 unit tests (no container needed)
mvn clean verify           # full build + tests
```

- Deploy the **EAR** (not the WARs) to `EAP_HOME/standalone/deployments/`.
- UI: `http://<host>:8080/jboss/logs/viewer/index.html`
- API: `http://<host>:8080/jboss/logs/viewer/api/{tree,entries,content}`

## How to work here

- Follow the **milestones M1–M8** in `implementation-plan.md` §10. Each milestone must
  **compile, ship its own tests, and pass its validation gate** before the next begins.
- A milestone is not "done" until `mvn test` is green (logic layers) or the documented
  `curl`/browser smoke check passes (web layers).
- Match the surrounding code's style, naming, and comment density.
- Keep the two WARs decoupled: no shared runtime state, no cross-WAR classpath assumptions.

## File map

- `implementation-plan.md` — design + milestone build order (read first)
- `jboss-log-viewer-api/` — Java logic, servlets, unit tests
- `jboss-log-viewer-web/` — `index.html`, `css/`, `js/`
- `jboss-log-viewer-ear/` — EAR assembly
- `README.md` — build/deploy/usage for end users
