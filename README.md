# JBoss Log Viewer

A **Jakarta EE 10** web application for browsing **JBoss EAP 8+ / WildFly** server and
application log files from the browser. It presents a file tree on the left and a live,
auto-scrolling text view on the right, with optional auto-refresh and transparent decompression
of archived (rotated) logs.

> **Status:** Feature-complete (milestones M1–M8). Built and unit-tested locally; the
> deployment smoke test below should be run on a real WildFly/EAP. See
> [`implementation-plan.md`](implementation-plan.md) for the design and milestone build order.

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

## Run with Docker (JBoss EAP 8.1, Java 21)

The repository ships a [`docker/Dockerfile`](docker/Dockerfile) and
[`docker-compose.yml`](docker-compose.yml) that run the application on **JBoss EAP 8.1**
(OpenJDK 21, matching the application's Java 21 target). The EAR is **mounted** into the server's
deployments directory at run time (not baked into the image), so you can rebuild the app and
redeploy by just restarting the container.

### Prerequisites

- Docker (with Compose v2) or Podman.
- **A Red Hat subscription.** The official EAP 8.1 image is hosted on the authenticated Red Hat
  registry. Log in once:

  ```bash
  docker login registry.redhat.io
  ```

  If you do not have a subscription, use the [WildFly alternative](#no-subscription-wildfly)
  below — it runs the identical EAR from a freely pullable image.

### Build the EAR and start the container

```bash
mvn clean package                 # produces jboss-log-viewer-ear/target/jboss-log-viewer.ear
docker compose up --build         # builds the EAP 8.1 image and starts the server
```

What the compose file does:

- **Mounts the EAR** read-only at `/opt/eap/standalone/deployments/jboss-log-viewer.ear`, which
  EAP auto-deploys on startup.
- **Mounts `./app-logs`** (host) at `/var/log/app` (container) as the application log directory,
  seeded with a sample log so the viewer has content to show. Drop your own `*.log` /
  `*.log.gz` / `*.tar.gz` files into `./app-logs` to see them in the **Application** tab.
- Sets `JBOSS_SERVER_LOG_DIR=/opt/eap/standalone/log` (EAP's own `server.log`, shown in the
  **Server** tab) and `JBOSS_APP_LOG_DIR=/var/log/app`.
- Publishes **8080** (HTTP) and **9990** (management console).

### View the application

Once the log shows the server is started and the EAR deployed, open:

```
http://localhost:8080/jboss/logs/viewer/index.html
```

Toggle between **Server** and **Application** logs in the top bar.

### Redeploy after a code change

```bash
mvn clean package
docker compose restart            # EAP picks up the rebuilt, mounted EAR
```

### Stop

```bash
docker compose down
```

### Build without compose

```bash
docker build -f docker/Dockerfile -t jboss-log-viewer:eap81 .
docker run --rm -p 8080:8080 \
  -v "$(pwd)/jboss-log-viewer-ear/target/jboss-log-viewer.ear:/opt/eap/standalone/deployments/jboss-log-viewer.ear:ro" \
  -v "$(pwd)/app-logs:/var/log/app" \
  jboss-log-viewer:eap81 -b 0.0.0.0
```

<a id="no-subscription-wildfly"></a>
### No Red Hat subscription? Use WildFly

WildFly is the upstream of JBoss EAP and runs the same Jakarta EE 10 EAR. Its image is public:

```bash
mvn clean package
docker run --rm -p 8080:8080 -p 9090:9090 \
  -v "$(pwd)/jboss-log-viewer-ear/target/jboss-log-viewer.ear:/opt/jboss/wildfly/standalone/deployments/jboss-log-viewer.ear:ro" \
  -e JBOSS_SERVER_LOG_DIR=/opt/jboss/wildfly/standalone/log \
  -e JBOSS_APP_LOG_DIR=/opt/jboss/wildfly/standalone/log \
  quay.io/wildfly/wildfly:latest-jdk21 \
  /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0
```

Then open the same URL: `http://localhost:8080/jboss/logs/viewer/index.html`.

> The WildFly deployments path is `/opt/jboss/wildfly/standalone/deployments` (vs. EAP's
> `/opt/eap/standalone/deployments`). Everything else — context roots, env vars, UI — is identical.

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

Then load the UI in a browser (`/jboss/logs/viewer/index.html`) and confirm each item:

- [ ] the file tree renders and toggles between **Server** and **Application**;
- [ ] selecting a file shows its tail, scrolled to the end;
- [ ] enabling **Auto-refresh (5s)** appends new lines (verify with
      `echo "line" >> "$JBOSS_SERVER_LOG_DIR/server.log"`);
- [ ] scrolling up pauses auto-pin; scrolling back to the bottom resumes it;
- [ ] dragging the divider resizes the panes and the width persists across a reload;
- [ ] selecting a compressed file (`.gz`/`.zip`/`.tar.gz`) shows decompressed text with a
      "decompressed" badge and auto-refresh disabled;
- [ ] a multi-entry archive shows the entry picker and switching entries reloads the content.

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
