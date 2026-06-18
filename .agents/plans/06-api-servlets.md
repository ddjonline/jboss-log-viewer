# 06 — M6: API Servlets

> **Goal:** expose the service layer over HTTP as JSON, under the API WAR context root.
>
> **Prerequisites:** M2–M5 (config, security, listing, tail/codec). Read
> [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-api`)

### `json/JsonSupport`
- Convert `LogNode` (recursively) and `TailResult` and entry lists to `JsonObject`/`JsonArray`
  using **Jakarta JSON-P** (`Json.createObjectBuilder`, `createArrayBuilder`). No Jackson/Gson.

### Servlets (short mappings — API context root supplies `/jboss/logs/viewer/api`)

| Servlet | `@WebServlet` | Final URL | Params | Response |
|---|---|---|---|---|
| `TreeServlet` | `/tree` | `…/api/tree` | `set=server\|application` | JSON `LogNode` tree |
| `EntriesServlet` | `/entries` | `…/api/entries` | `set`, `path` (archive) | JSON `[{name, size}]` |
| `ContentServlet` | `/content` | `…/api/content` | `set`, `path`, `entry?`, `offset?` (def -1), `max?` | JSON `{content, nextOffset, fileSize, truncated, compressed}` |

- Each servlet pulls `LogDirectoryConfig` from the `ServletContext` and passes it into the
  service classes (services stay constructor-injected / container-free).
- Every path parameter goes through `PathSecurity`.
- `Content-Type: application/json`, UTF-8.

### `ApiErrorHandler`
- Central mapping to JSON `{error, message}` + status: `400` invalid/missing param, `403` path
  escape (from `PathSecurity`), `404` not found, `500` unexpected.
- Log access and errors via SLF4J.

Remove the M1 `PingServlet` (or keep it as an intentional health check).

## Validate

Rebuild + redeploy the EAR; scripted `curl` against a seeded log dir (under
`http://localhost:8080/jboss/logs/viewer/api`):
- `/api/tree?set=server` → expected JSON tree;
- `/api/content?set=server&path=server.log` → tail JSON with `nextOffset`;
- append to the file, re-`curl` with `offset=<nextOffset>` → only new bytes returned;
- `/api/entries?set=server&path=<archive>` → entry list; `/api/content` with `&entry=` → text;
- traversal probe `path=../../etc/passwd` → HTTP `403` JSON error.

## Gate

Every documented `curl` returns the expected status/body.

## Notes

- Keep servlet code thin: parse params → validate path → call service → serialize via
  `JsonSupport` → handle errors via `ApiErrorHandler`.
- Mappings stay short; do not embed the context-root prefix.
