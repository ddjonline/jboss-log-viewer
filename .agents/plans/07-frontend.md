# 07 — M7: Frontend (Web WAR)

> **Goal:** the browser UI — tree, toggle, live content view, auto-refresh, draggable divider,
> archive entry picker — in pure HTML/CSS/vanilla JS.
>
> **Prerequisites:** M6 (API endpoints live). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-web/src/main/webapp`)

### `index.html`
- Top bar: title; **Server ⟷ Application** toggle; **Auto-refresh (5 s)** checkbox; manual
  Refresh button; current file name + size indicator.
- Two-pane layout: **left** = scrollable tree (collapsible dirs, file = leaf); **right** = `<pre>`
  monospace content view, scrollable.
- **Draggable divider** between panes.

### `css/styles.css`
- Clean neutral theme; monospace content pane; the splitter bar styling. No CSS frameworks.

### `js/app.js` (ES2021, `fetch`, no libraries)
- All API calls **relative to the page** (`./api/tree`, `./api/content`, `./api/entries`). Never
  hardcode host/context root.
- **Tree:** on load and on toggle change → `GET ./api/tree?set=…`, render recursively; dirs
  expand/collapse; click a file to select; highlight the active file.
- **Content:** on select → reset cursor (`offset = -1`), `GET ./api/content`, replace `<pre>`
  content via `textContent` (never `innerHTML`), set `scrollTop = scrollHeight` (auto-scroll to
  end), store `nextOffset`.
- **Auto-refresh:** when checked, `setInterval(poll, 5000)`:
  - `GET ./api/content?...&offset=<nextOffset>`; if `truncated` → full reload; else **append**
    new content;
  - keep pinned to bottom **only if** the user was already at/near the bottom;
  - update `nextOffset`. Clear the interval on toggle-off, file change, and page unload.
- **Compressed files** (`compressed` true from the tree/content): show decompressed text with a
  small "decompressed" badge and **disable auto-refresh** for that selection.
- **Multi-entry archives:** when selected, call `GET ./api/entries`, show an **entry picker**;
  the chosen entry is passed to `./api/content` as `&entry=`. Single-entry archives open directly.
- **Divider:** drag to resize; persist the width in `localStorage`; restore on load.

## Validate

Browser smoke test on the seeded log dir:
- tree renders and toggles between server/application;
- selecting a file shows the tail scrolled to the end;
- enabling auto-refresh appends new lines every 5 s (echo into the file to verify);
- divider drag persists across reload;
- selecting a compressed file shows decompressed text with auto-refresh disabled;
- a multi-entry archive shows the entry picker.

## Gate

Each behavior observed in the browser (capture the checklist in the README, M8).

## Notes

- `textContent` only for log content — log lines are untrusted (XSS).
- No build step: ship the files as-is in the Web WAR.
