# 07 — M7: Frontend (Web WAR)

> **Goal:** the browser UI — tree, toggle, live content view, auto-refresh, draggable divider,
> archive entry picker — in pure HTML/CSS/vanilla JS.
>
> **Prerequisites:** M6 (API endpoints live). Read [`00-overview.md`](00-overview.md).

## Build (in `jboss-log-viewer-web/src/main/webapp`)

### `index.html`
- Top bar: title; **Server ⟷ Application** toggle; **Auto-refresh (5 s)** checkbox; manual
  Refresh button; current file name, **size and last updated timestamp indicator**; **Download button for selected log file**; **Search field for text within files**; **Title turns green for ~1s on content updates**.
- Two-pane layout: **left** = scrollable tree (collapsible dirs, file = leaf); **right** = `<pre>`
  monospace content view, scrollable.
 - **Footer:** displays the absolute filesystem path of the selected file.
- **Draggable divider** between panes.

### `css/styles.css`
- Clean neutral theme; monospace content pane; the splitter bar styling; **highlighting style for search results**; **styling for file metadata display**; **title color pulse style**; **footer styling**. No CSS frameworks.

### `js/app.js` (ES2021, `fetch`, no libraries)
- All API calls **relative to the page** (`./api/tree`, `./api/content`, `./api/entries`). Never
  hardcode host/context root.
- **Tree:** on load and on toggle change → `GET ./api/tree?set=…`, render recursively; dirs
  expand/collapse; click a file to select; highlight the active file; display file `size` and `lastModified`.
  Update footer with the full path of the selected file.
- **Content:** on select → reset cursor (`offset = -1`), `GET ./api/content`, replace `<pre>`
  content via `textContent` (never `innerHTML`), set `scrollTop = scrollHeight` (ensure auto-scroll to end);
  store `nextOffset`.
- **Download:** on download button click → `fetch` the same content URL used for display;
  create a Blob from the response text and use `URL.createObjectURL` to download with the
  original file name. Note: this downloads the currently displayed content window
  (tail window for plain files, decompressed view for archives), not the unbounded full file.
  A future enhancement may add a dedicated capped full-file download endpoint in M6.
- **Search:** on search input, dynamically highlight search results within the content area by
  applying a styled CSS class to matches.
- **Auto-refresh:** when checked, `setInterval(poll, 5000)`:
  - `GET ./api/content?...&offset=<nextOffset>`; if `truncated` → full reload; else **append**
    new content; temporarily turn the page title color to green (~1s) on new content arrival;
  - keep pinned to bottom **only if** the user was already at/near the bottom;
  - update `nextOffset`. Clear the interval on toggle-off, file change, and page unload.
- **Compressed files** (`compressed` true from the tree/content): show decompressed text with a
  small "decompressed" badge and **disable auto-refresh** for that selection.
- **Multi-entry archives:** when selected, call `GET ./api/entries`, show an **entry picker**;
  the chosen entry is passed to `./api/content` as `&entry=`. Single-entry archives open directly.
- **Divider:** drag to resize; persist the width in `localStorage`; restore on load.

Implementation notes:
- Query DOM elements after the DOM is ready. If the script tag appears before footer markup,
  reacquire the footer element (`#footer-path`) inside `DOMContentLoaded`/`init` before updating
  its text, otherwise it will be `null` and never update.
 - Footer uses `absolutePath` returned by `./api/content` once available; before the first
   content load it may briefly show nothing or a relative path as a fallback.

## Validate

Browser smoke test on the seeded log dir:
- tree renders and toggles between server/application;
- selecting a file shows the tail scrolled to the end;
- enabling auto-refresh appends new lines every 5 s (echo into the file to verify);
 - **download the currently displayed content window and verify file name and content**;
- **search within file content and verify highlights appear correctly**;
- **display and verify file size and last updated timestamp are correct**;
- **verify auto-scroll to end and title color pulse on content refresh**;
- **verify footer displays the full path of the selected file correctly**;
- divider drag persists across reload;
- selecting a compressed file shows decompressed text with auto-refresh disabled;
- a multi-entry archive shows the entry picker.

## Gate

Each behavior observed in the browser (capture the checklist in the README, M8).

## Notes

- `textContent` only for log content — log lines are untrusted (XSS).
- No build step: ship the files as-is in the Web WAR.
 - The update indicator is a brief green color pulse on the title (~1s) when new content is loaded/appended.
