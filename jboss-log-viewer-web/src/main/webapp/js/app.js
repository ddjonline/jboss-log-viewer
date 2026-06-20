/*
 * JBoss Log Viewer — vanilla ES2021, no libraries, no build step.
 * All API calls are relative to this page so they resolve under the
 * Web WAR context root (/jboss/logs/viewer) to the API WAR
 * (/jboss/logs/viewer/api/...).
 */
'use strict';

const DEFAULT_REFRESH_MS = 5000;
const BOTTOM_THRESHOLD_PX = 40;
const DIVIDER_KEY = 'jbossLogViewer.treeWidth';

// Title pulse: temporarily turn the title green on updates
let titlePulseTimer = null;
function triggerTitlePulse() {
    const title = document.querySelector('h1.title');
    if (!title) return;
    if (titlePulseTimer) {
        clearTimeout(titlePulseTimer);
        titlePulseTimer = null;
    }
    // Remove any previous inline color to allow re-applying the pulse
    title.style.removeProperty('color');
    // Apply on next frame to ensure consistent paint even during frequent updates
    requestAnimationFrame(() => {
        title.style.setProperty('color', '#15803d', 'important'); // force visible green
        titlePulseTimer = setTimeout(() => {
            title.style.removeProperty('color');
        }, 1000);
    });
}

const state = {
    set: 'server',          // 'server' | 'application'
    selected: null,         // { path, name, compressed }
    entry: null,            // selected archive entry name, or null
    nextOffset: -1,
    timer: null,
    refreshMs: DEFAULT_REFRESH_MS,
};

// ---- DOM refs ----
const el = {
    treePane: document.getElementById('tree-pane'),
    tree: document.getElementById('tree'),
    divider: document.getElementById('divider'),
    content: document.getElementById('content'),
    fileInfo: document.getElementById('file-info'),
    footer: document.getElementById('footer-path'),
    autoRefresh: document.getElementById('auto-refresh'),
    refreshBtn: document.getElementById('refresh-btn'),
    refreshSelect: document.getElementById('refresh-interval'),
    downloadBtn: document.getElementById('download-btn'),
    setServer: document.getElementById('set-server'),
    setApplication: document.getElementById('set-application'),
    entryPicker: document.getElementById('entry-picker'),
    entrySelect: document.getElementById('entry-select'),
};

// ---- API helpers ----
async function apiGet(path, params) {
    const url = new URL(path, window.location.href);
    Object.entries(params || {}).forEach(([k, v]) => {
        if (v !== null && v !== undefined) url.searchParams.set(k, v);
    });
    const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
    const body = await resp.json().catch(() => ({}));
    if (!resp.ok) {
        const msg = body && body.message ? body.message : `HTTP ${resp.status}`;
        throw new Error(msg);
    }
    return body;
}

// ---- Tree rendering ----
async function loadTree() {
    el.tree.innerHTML = '';
    let data;
    try {
        data = await apiGet('./api/tree', { set: state.set });
    } catch (e) {
        renderTreeError(e.message);
        return;
    }
    const children = data.children || [];
    if (children.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'empty';
        empty.textContent = 'No log files found.';
        el.tree.appendChild(empty);
        return;
    }
    el.tree.appendChild(buildList(children));
}

function renderTreeError(message) {
    const div = document.createElement('div');
    div.className = 'empty';
    div.textContent = 'Error loading tree: ' + message;
    el.tree.appendChild(div);
}

function buildList(nodes) {
    const ul = document.createElement('ul');
    for (const node of nodes) {
        ul.appendChild(node.type === 'directory' ? buildDir(node) : buildFile(node));
    }
    return ul;
}

function buildDir(node) {
    const li = document.createElement('li');
    const row = document.createElement('div');
    row.className = 'node dir';

    const twisty = document.createElement('span');
    twisty.className = 'twisty';
    twisty.textContent = '▾';

    const label = document.createElement('span');
    label.className = 'label';
    label.textContent = node.name;

    row.append(twisty, label);
    li.appendChild(row);

    const childList = buildList(node.children || []);
    li.appendChild(childList);

    row.addEventListener('click', () => {
        const collapsed = childList.style.display === 'none';
        childList.style.display = collapsed ? '' : 'none';
        twisty.textContent = collapsed ? '▾' : '▸';
    });
    return li;
}

function buildFile(node) {
    const li = document.createElement('li');
    const row = document.createElement('div');
    row.className = 'node file';
    row.dataset.path = node.path;

    const twisty = document.createElement('span');
    twisty.className = 'twisty';
    twisty.textContent = node.compressed ? '🗜' : '·';

    const label = document.createElement('span');
    label.className = 'label';
    label.textContent = node.name;

    const meta = document.createElement('span');
    meta.className = 'file-meta';
    const ts = typeof node.lastModified === 'number' ? new Date(node.lastModified).toLocaleString() : '';
    meta.textContent = [formatSize(node.size), ts].filter(Boolean).join(' · ');

    row.append(twisty, label, meta);
    li.appendChild(row);

    row.addEventListener('click', () => selectFile(node, row));
    return li;
}

// ---- File selection ----
async function selectFile(node, row) {
    stopAutoRefresh();
    document.querySelectorAll('.tree .node.selected')
        .forEach((n) => n.classList.remove('selected'));
    row.classList.add('selected');

    state.selected = { path: node.path, name: node.name, compressed: !!node.compressed, lastModified: node.lastModified };
    state.entry = null;
    state.nextOffset = -1;
    el.entryPicker.hidden = true;
    el.entrySelect.innerHTML = '';
    // Enable Download now that a file is selected
    if (el.downloadBtn) {
        el.downloadBtn.disabled = false;
        el.downloadBtn.removeAttribute('disabled');
    }

    // Compressed: auto-refresh does not apply.
    el.autoRefresh.disabled = node.compressed;
    if (el.refreshSelect) el.refreshSelect.disabled = node.compressed;
    if (node.compressed) {
        el.autoRefresh.checked = false;
        await prepareArchive(node);
    } else {
        await loadContent(true);
    }
    updateAutoRefresh();
}

async function prepareArchive(node) {
    // Ask the API for entries; a multi-entry archive shows the picker.
    let entries = [];
    try {
        entries = await apiGet('./api/entries', { set: state.set, path: node.path });
    } catch (e) {
        // Treat as a single-stream archive (e.g. plain .gz) — open directly.
        entries = [];
    }
    if (entries.length > 1) {
        el.entrySelect.innerHTML = '';
        for (const entry of entries) {
            const opt = document.createElement('option');
            opt.value = entry.name;
            opt.textContent = `${entry.name} (${formatSize(entry.size)})`;
            el.entrySelect.appendChild(opt);
        }
        el.entryPicker.hidden = false;
        state.entry = entries[0].name;
    } else {
        state.entry = entries.length === 1 ? entries[0].name : null;
    }
    await loadContent(true);
}

async function loadContent(scrollToEnd) {
    if (!state.selected) return;
    let data;
    try {
        data = await apiGet('./api/content', {
            set: state.set,
            path: state.selected.path,
            entry: state.entry,
            offset: -1,
        });
    } catch (e) {
        el.content.classList.add('placeholder');
        el.content.textContent = 'Error: ' + e.message;
        return;
    }
    el.content.classList.remove('placeholder');
    el.content.textContent = data.content;            // textContent → XSS-safe
    state.nextOffset = data.nextOffset;
    updateFileInfo(data);
    if (scrollToEnd) scrollContentToEnd();
    triggerTitlePulse();
    // Ensure Download is enabled after successful load
    if (el.downloadBtn) {
        el.downloadBtn.disabled = false;
        el.downloadBtn.removeAttribute('disabled');
    }
}

// ---- Auto-refresh polling ----
async function poll() {
    if (!state.selected || state.selected.compressed) return;
    let data;
    try {
        data = await apiGet('./api/content', {
            set: state.set,
            path: state.selected.path,
            offset: state.nextOffset,
        });
    } catch (e) {
        return; // transient; try again next tick
    }

    if (data.truncated) {
        // Rotation/truncation: full reload.
        el.content.textContent = data.content;
        state.nextOffset = data.nextOffset;
        updateFileInfo(data);
        scrollContentToEnd();
        triggerTitlePulse();
        if (el.downloadBtn) {
            el.downloadBtn.disabled = false;
            el.downloadBtn.removeAttribute('disabled');
        }
        return;
    }

    if (data.content && data.content.length > 0) {
        const atBottom = isNearBottom();
        el.content.appendChild(document.createTextNode(data.content));
        state.nextOffset = data.nextOffset;
        updateFileInfo(data);
        if (atBottom) scrollContentToEnd();
        triggerTitlePulse();
        if (el.downloadBtn) {
            el.downloadBtn.disabled = false;
            el.downloadBtn.removeAttribute('disabled');
        }
    } else {
        state.nextOffset = data.nextOffset;
    }
}

function updateAutoRefresh() {
    if (el.autoRefresh.checked && !el.autoRefresh.disabled && state.selected) {
        startAutoRefresh();
    } else {
        stopAutoRefresh();
    }
}

function startAutoRefresh() {
    stopAutoRefresh();
    const interval = typeof state.refreshMs === 'number' && state.refreshMs > 0
        ? state.refreshMs
        : DEFAULT_REFRESH_MS;
    state.timer = window.setInterval(poll, interval);
}

function stopAutoRefresh() {
    if (state.timer !== null) {
        window.clearInterval(state.timer);
        state.timer = null;
    }
}

// ---- Content helpers ----
function isNearBottom() {
    const c = el.content;
    return c.scrollHeight - c.scrollTop - c.clientHeight <= BOTTOM_THRESHOLD_PX;
}

function scrollContentToEnd() {
    // Defer until layout is applied to ensure scrollHeight is final
    requestAnimationFrame(() => {
        el.content.scrollTop = el.content.scrollHeight;
    });
}

function updateFileInfo(data) {
    if (!state.selected) {
        el.fileInfo.textContent = '';
        return;
    }
    // Persist absolute path if provided by the API so the footer can show it.
    if (data && typeof data.absolutePath === 'string' && data.absolutePath.length > 0) {
        state.selected.absolutePath = data.absolutePath;
    }
    let text = `${state.selected.name} — ${formatSize(data.fileSize)}`;
    el.fileInfo.textContent = text;
    if (data.compressed) {
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = 'decompressed';
        el.fileInfo.appendChild(badge);
    }
    updateFooter();
}

function formatSize(bytes) {
    if (bytes === null || bytes === undefined) return '';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = bytes;
    let i = 0;
    while (size >= 1024 && i < units.length - 1) {
        size /= 1024;
        i++;
    }
    return (i === 0 ? size : size.toFixed(1)) + ' ' + units[i];
}

// ---- Log set toggle ----
function selectSet(set) {
    if (state.set === set) return;
    state.set = set;
    el.setServer.classList.toggle('active', set === 'server');
    el.setApplication.classList.toggle('active', set === 'application');
    // Reset selection on set change.
    stopAutoRefresh();
    state.selected = null;
    state.entry = null;
    state.nextOffset = -1;
    el.entryPicker.hidden = true;
    el.content.textContent = '';
    el.content.classList.add('placeholder');
    el.fileInfo.textContent = '';
    if (el.refreshSelect) el.refreshSelect.disabled = false;
    if (el.downloadBtn) {
        el.downloadBtn.disabled = true;
        el.downloadBtn.setAttribute('disabled', '');
    }
    updateFooter();
    loadTree();
}

// ---- Draggable divider ----
function initDivider() {
    const saved = window.localStorage.getItem(DIVIDER_KEY);
    if (saved) el.treePane.style.width = saved;

    let dragging = false;

    const onMove = (clientX) => {
        const layoutRect = document.getElementById('layout').getBoundingClientRect();
        let width = clientX - layoutRect.left;
        const min = 140;
        const max = layoutRect.width - 200;
        width = Math.max(min, Math.min(width, max));
        el.treePane.style.width = width + 'px';
    };

    el.divider.addEventListener('mousedown', (e) => {
        dragging = true;
        e.preventDefault();
        document.body.style.userSelect = 'none';
    });
    window.addEventListener('mousemove', (e) => {
        if (dragging) onMove(e.clientX);
    });
    window.addEventListener('mouseup', () => {
        if (dragging) {
            dragging = false;
            document.body.style.userSelect = '';
            window.localStorage.setItem(DIVIDER_KEY, el.treePane.style.width);
        }
    });

    // Keyboard resize for accessibility.
    el.divider.addEventListener('keydown', (e) => {
        const cur = el.treePane.getBoundingClientRect().width;
        if (e.key === 'ArrowLeft') onMove(cur - 16 + document.getElementById('layout').getBoundingClientRect().left);
        else if (e.key === 'ArrowRight') onMove(cur + 16 + document.getElementById('layout').getBoundingClientRect().left);
        else return;
        window.localStorage.setItem(DIVIDER_KEY, el.treePane.style.width);
    });
}

// ---- Wiring ----
function init() {
    // Footer element is after the script tag in the DOM; reacquire now that DOM is ready.
    el.footer = document.getElementById('footer-path');
    el.content.classList.add('placeholder');
    el.content.textContent = 'Select a log file from the tree.';
    updateFooter();

    el.setServer.addEventListener('click', () => selectSet('server'));
    el.setApplication.addEventListener('click', () => selectSet('application'));

    el.autoRefresh.addEventListener('change', updateAutoRefresh);
    if (el.refreshSelect) {
        el.refreshSelect.value = String(DEFAULT_REFRESH_MS);
        el.refreshSelect.addEventListener('change', () => {
            const v = parseInt(el.refreshSelect.value, 10);
            state.refreshMs = Number.isFinite(v) && v > 0 ? v : DEFAULT_REFRESH_MS;
            if (el.autoRefresh.checked && !el.autoRefresh.disabled && state.selected) {
                startAutoRefresh(); // restart with new interval
            }
        });
    }
    el.refreshBtn.addEventListener('click', () => {
        if (state.selected) loadContent(true);
    });
    el.downloadBtn.addEventListener('click', onDownload);
    el.entrySelect.addEventListener('change', () => {
        state.entry = el.entrySelect.value;
        state.nextOffset = -1;
        loadContent(true);
        if (el.downloadBtn) {
            el.downloadBtn.disabled = false;
            el.downloadBtn.removeAttribute('disabled');
        }
    });

    window.addEventListener('beforeunload', stopAutoRefresh);

    initDivider();
    loadTree();
}

document.addEventListener('DOMContentLoaded', init);

// Footer shows the full selected path and current log set
function updateFooter() {
    if (!el.footer) return;
    if (!state.selected) {
        el.footer.textContent = '';
        return;
    }
    const setLabel = state.set === 'server' ? 'Server' : 'Application';
    const entrySuffix = state.entry ? ` [${state.entry}]` : '';
    const abs = state.selected.absolutePath;
    const shown = abs && abs.length > 0 ? abs : state.selected.path;
    el.footer.textContent = `${setLabel} logs: ${shown}${entrySuffix}`;
}

// ---- Download current window of the selected log ----
async function onDownload() {
    if (!state.selected) return;
    try {
        // Use the same content endpoint. For large files this is a tail window per server cap.
        const url = new URL('./api/content', window.location.href);
        url.searchParams.set('set', state.set);
        url.searchParams.set('path', state.selected.path);
        if (state.entry) url.searchParams.set('entry', state.entry);
        url.searchParams.set('offset', -1);

        const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        const text = data && data.content ? data.content : '';

        const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
        const a = document.createElement('a');
        const href = URL.createObjectURL(blob);
        a.href = href;
        const base = state.selected.name || 'log.txt';
        const entrySuffix = state.entry ? `__${state.entry.replaceAll('/', '_')}` : '';
        a.download = `${base}${entrySuffix}.txt`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(href);
    } catch (e) {
        // eslint-disable-next-line no-console
        console.error('Download failed:', e);
    }
}
