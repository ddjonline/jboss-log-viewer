# 01 — M1: Multi-module Skeleton + EAR + Context Roots

> **Goal:** prove the EE/packaging plumbing — WAR build, EAR assembly, dual context roots,
> JSON-P, and longest-prefix routing — with near-zero logic, before anything depends on it.
>
> **Prerequisites:** none (first milestone). Read [`00-overview.md`](00-overview.md) first.

## Build

Create the Maven multi-module reactor and a trivial deployable EAR.

### Parent reactor `pom.xml` (`packaging=pom`)
- `<modules>`: `jboss-log-viewer-api`, `jboss-log-viewer-web`, `jboss-log-viewer-ear`.
- Shared `<properties>`: `maven.compiler.release=21`, dependency/plugin versions.
- `<dependencyManagement>`: pin Jakarta EE Web API 10, SLF4J 2.0.x, Commons Compress 1.26.x,
  JUnit Jupiter 5.x.
- Plugin management: compiler (`<release>21</release>`), war, ear, surefire.

### `jboss-log-viewer-api` (`packaging=war`)
- Dependencies: `jakarta.platform:jakarta.jakartaee-web-api:10.0.0` (**provided**),
  `org.slf4j:slf4j-api` (**provided**), `org.junit.jupiter:junit-jupiter` (**test**).
- `src/main/webapp/WEB-INF/jboss-web.xml` → `<context-root>/jboss/logs/viewer/api</context-root>`.
- `PingServlet` — `@WebServlet("/ping")`, `GET` returns JSON `{"status":"ok"}` built with
  **Jakarta JSON-P** (`Json.createObjectBuilder()`), `Content-Type: application/json`.

### `jboss-log-viewer-web` (`packaging=war`)
- No Java sources, no compile dependencies.
- `src/main/webapp/WEB-INF/jboss-web.xml` → `<context-root>/jboss/logs/viewer</context-root>`.
- `src/main/webapp/index.html` — placeholder page (a heading is enough for M1).

### `jboss-log-viewer-ear` (`packaging=ear`)
- `maven-ear-plugin` with both WARs as `<webModule>` entries; EAR final name
  `jboss-log-viewer`. Context roots come from each WAR's `jboss-web.xml`.
- Depends on the api and web modules (reactor order: api/web → ear).
- `src/main/application/META-INF/jboss-deployment-structure.xml` declares the server
  `org.slf4j` module dependency for the top-level EAR deployment and the
  `jboss-log-viewer-api.war` sub-deployment. Do not bundle an SLF4J implementation or
  `slf4j-api` in the EAR/WAR.

## Validate

1. `mvn clean package` at the parent builds all modules and produces
   `jboss-log-viewer-ear/target/jboss-log-viewer.ear` containing both WARs.
2. Confirm the EAR contains `META-INF/jboss-deployment-structure.xml`.
3. Deploy the EAR to a local WildFly/EAP.
4. API context root + servlet mapping + JSON-P:
   ```bash
   curl -s http://localhost:8080/jboss/logs/viewer/api/ping
   # expect: {"status":"ok"}
   ```
5. Web WAR context root + longest-prefix routing:
   open `http://localhost:8080/jboss/logs/viewer/index.html` in a browser — placeholder loads.

## Gate

Both URLs respond as expected **from the single deployed EAR**. The two context roots coexist
(API requests reach the API WAR, the UI path reaches the Web WAR).

`PingServlet` is retained as a health check or removed in M6.

## Notes

- Do not hardcode the `/jboss/logs/viewer/api` prefix in `@WebServlet` — use `/ping`.
- No business logic in this milestone; it exists purely to de-risk packaging and routing.
