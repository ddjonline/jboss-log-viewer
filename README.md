# JBoss Log Viewer

A **Jakarta EE 10** web application for browsing **JBoss EAP 8+ / WildFly** server and
application log files from the browser. It presents a file tree on the left and a live,
auto-scrolling text view on the right, with optional auto-refresh and transparent decompression
of archived (rotated) logs.

> **Status:** In development. See [`implementation-plan.md`](implementation-plan.md) for the
> design and milestone build order.

## Features

- **File tree** of the configured log directory (left pane), filtered to log and archive files.
- **Toggle** between **Server logs** and **Application logs**.
- **Live content view** (right pane) auto-scrolled to the end of the selected file.
- **Auto-refresh** every 5 seconds (toggle) — appends only newly written bytes, and keeps the
  view pinned to the bottom only if you were already there.
- **Transparent decompression** of `.gz`, `.gzip`, `.zip`, `.tar.gz`, and `.tgz` logs, with an
  **entry picker** for multi-file archives.
- **Draggable divider** between the two panes (width remembered across sessions).
- Read-only and path-traversal-hardened.

## Requirements

- **Java 21** (JDK)
- **Maven 3.9+**
- **JBoss EAP 8+** or **WildFly 30+** as the deployment target

## Technology

- Jakarta EE 10 (Servlet 6.0, Jakarta JSON-P) — plain `HttpServlet`, no application frameworks
- SLF4J for logging (binding provided by the application server)
- Apache Commons Compress (TAR archive support)
- Pure HTML / CSS / vanilla JavaScript frontend (no build step, no npm)

## Project structure

This is a Maven multi-module project that produces a single deployable **EAR** containing two
WARs:

| Module | Artifact | Context root | Contents |
|---|---|---|---|
| `jboss-log-viewer-api` | WAR | `/jboss/logs/viewer/api` | REST-style JSON endpoints + all Java logic |
| `jboss-log-viewer-web` | WAR | `/jboss/logs/viewer` | Static UI (`index.html`, CSS, JS) |
| `jboss-log-viewer-ear` | EAR | — | Bundles both WARs (`jboss-log-viewer.ear`) |

## Configuration

The two log root directories are supplied via **environment properties**. Each resolves in this
order: **environment variable → JVM system property → default**.

| Purpose | Environment variable | System-property fallback | Default |
|---|---|---|---|
| Server logs | `JBOSS_SERVER_LOG_DIR` | `jboss.server.log.dir` | `EAP_HOME/standalone/log` |
| Application logs | `JBOSS_APP_LOG_DIR` | `app.log.dir` | falls back to the server log dir |

If a configured directory is missing or unreadable, the app logs a warning and shows an empty
tree for that set rather than failing to start.

## Build

From the project root:

```bash
mvn clean package
```

This builds all modules in order and produces the deployable artifact:

```
jboss-log-viewer-ear/target/jboss-log-viewer.ear
```

Run the unit tests on their own with:

```bash
mvn test
```

## Deploy

1. Set the log directory environment variables before starting the server, e.g.:

   ```bash
   export JBOSS_SERVER_LOG_DIR="$EAP_HOME/standalone/log"
   export JBOSS_APP_LOG_DIR="/var/log/myapp"
   ```

2. Copy the EAR into the server's deployments directory:

   ```bash
   cp jboss-log-viewer-ear/target/jboss-log-viewer.ear "$EAP_HOME/standalone/deployments/"
   ```

   (Or deploy via the management console / `jboss-cli`.)

3. Open the UI:

   ```
   http://<host>:8080/jboss/logs/viewer/index.html
   ```

## URLs

| Resource | URL |
|---|---|
| Web UI | `http://<host>:8080/jboss/logs/viewer/index.html` |
| Tree API | `http://<host>:8080/jboss/logs/viewer/api/tree?set=server` |
| Archive entries API | `http://<host>:8080/jboss/logs/viewer/api/entries?set=server&path=<archive>` |
| Content API | `http://<host>:8080/jboss/logs/viewer/api/content?set=server&path=<file>` |

`set` is `server` or `application`.

## Smoke test

After deploying (with a seeded log directory):

```bash
# API reachable, JSON tree returned
curl -s "http://localhost:8080/jboss/logs/viewer/api/tree?set=server"

# Tail of a file (note nextOffset in the response)
curl -s "http://localhost:8080/jboss/logs/viewer/api/content?set=server&path=server.log"

# Append, then fetch only new bytes
echo "new line" >> "$JBOSS_SERVER_LOG_DIR/server.log"
curl -s "http://localhost:8080/jboss/logs/viewer/api/content?set=server&path=server.log&offset=<nextOffset>"

# Path traversal is rejected (expect HTTP 403)
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/jboss/logs/viewer/api/content?set=server&path=../../etc/passwd"
```

Then load the UI in a browser and confirm: the tree renders and toggles, selecting a file shows
its tail scrolled to the end, auto-refresh appends new lines, the divider drags and persists, and
selecting a compressed archive shows decompressed text (with an entry picker for multi-file
archives).

## Security notes

- **Read-only**: the application only lists and reads log files; there are no write endpoints.
- **Path traversal**: all client-supplied paths are canonicalized and confined to the configured
  roots; `../`, absolute paths, and symlink escapes are rejected.
- **Bounded reads**: only a tail window (default 256 KB) is returned, so large files never
  overwhelm the server or browser.
- **Authentication is out of scope** for this version — protect the application at the container
  or network layer (e.g. a reverse proxy, or `web.xml` security constraints) before exposing it.

## License

See repository for license details.
