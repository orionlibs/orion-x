# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The frontend of the Orion Engineering Digital Twin. It is **not** a bundled SPA — it is a hand-rolled,
jQuery-based, multi-page application with zero build step:

- `app/server.js` — a ~190-line Node `http` server (no dependencies, no `package.json`) that serves static
  files and reverse-proxies `/api` and `/websocket` to the Spring Boot backend (`../thing`, `thing-service`).
- `app/public/pages/*.html` — one hand-written full HTML document per page.
- `app/public/assets/js/orion.js` — the entire first-party runtime: a single global object `orionCommon`
  plus a `GaugeChart` class. Everything a page can do goes through it.
- `app/public/assets/css/orion.css` — the only first-party stylesheet (~415 lines); everything else under
  `assets/css/` and `assets/libs/` is a vendored Bootstrap 5 admin template.

There is **no package.json, no lockfile, no bundler, no linter, no test suite, and no CI config** anywhere in
this module. Do not invent npm scripts. `pom.xml` declares Maven coordinates
(`com.orion:digital-twin-frontend-app:0.0.1`) and nothing else — no parent, no modules, no plugins, no `src/`.
`mvn` builds nothing here; no other POM references it.

## Run

```bash
node app/server.js                                    # http://localhost:8081
PORT=8081 BACKEND_HOST=localhost BACKEND_PORT=8080 node app/server.js
```

Paths are `__dirname`-based, so cwd does not matter. Binds `0.0.0.0`. Files are `readFile`'d per request with
no caching, so **editing HTML/JS/CSS needs no restart** — only `server.js` changes do.

| Env var | Default |
|---|---|
| `PORT` | `8081` |
| `BACKEND_HOST` | `localhost` |
| `BACKEND_PORT` | `8080` |

Smoke test: `curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/`.

## Request routing (`app/server.js`)

Dispatch is on `pathname` only, in this order:

1. `/websocket*` → proxy to backend **verbatim** (prefix never stripped; backend must mount SockJS at `/websocket`).
2. `/api*` → proxy to backend with `/api` stripped: `"/api/repositories/count".substring(4)` → `/repositories/count`
   (leading slash retained). Note the guards disagree — routing tests `startsWith('/api')` but the rewrite tests
   `startsWith('/api/')`, so `/api` and `/apifoo` reach the backend with the prefix intact.
3. `/assets/*` → `app/public/assets/<rest>`.
4. everything else → `app/public/pages/<pathname>`; `/` → `index.html`; extensionless → `.html` appended.

So `/repoDetails?repo=x` serves `app/public/pages/repoDetails.html` and the query string is client-side only.
Any read error (ENOENT, EISDIR, EACCES) becomes a plain-text 404. `mimeTypes` is a small hard-coded map —
`.ico`, `.eot`, `.map`, `.mp4` fall through to `application/octet-stream`, and the `'.tpl': 'image/tpl'` entry is
nonsense with no matching files.

The `server.on('upgrade')` handler hand-writes a fixed `HTTP/1.1 101` and **never copies the backend's
handshake headers**, so `Sec-WebSocket-Accept` is dropped and a native browser WebSocket upgrade fails validation.
In practice SockJS falls back to `xhr`/`xhr_streaming`, which are ordinary HTTP and take the plain proxy branch.

There is no path-traversal guard; containment currently holds only because `new URL()` normalises `..` and the
path is never percent-decoded. Treat it as fragile. Backend request/response headers are forwarded wholesale and
the 502 body echoes `err.message` (leaking backend host:port).

## Anatomy of a page

A page is always **three files** plus optional fragments:

| Piece | Location | Example |
|---|---|---|
| Document | `app/public/pages/<page>.html` | `pages/index.html` |
| Module | `app/public/assets/js/<page>/<page>.js` | `assets/js/home/index.js` |
| Fragment(s) | `app/public/pages/imports/<page>/<thing>.html` | `imports/repos/reposTable.html` |

**`index.html` is the only correct reference implementation.** Everything under `sample-pages/`, `sample-js/`,
and `imports/sample-imports/` was `git mv`'d into those folders without updating a single path — see
[Known breakage](#known-breakage).

### The HTML shell

`<html>` carries the theme contract and is byte-identical on every page:

```html
<html lang="en" dir="ltr" data-nav-layout="vertical" data-theme-mode="dark" data-vertical-style="detached"
      data-toggled="detached-open" data-card-style="style1" data-card-background="background1">
```

`<head id="html-header">` has a fixed prologue — jquery-ui CSS, `jquery.min.js`, `jquery-ui.min.js`, popper,
`bootstrap.bundle.min.js`, `plugins/main.js`, `<link id="style">` bootstrap.min.css, `styles.css`, `icons.css`,
`simplebar.min.css` — then per-page opt-ins, then always `sevenSeg.js`, `orion.js`, and **`orion.css` last**
(it is the override layer and only wins because it loads last).

`<body id="yapily-admin">` (six `orion.css` rules are scoped to that id) must contain, in order:
`#loader`, then `.page` wrapping `<header id="header" class="app-header sticky">`,
`<aside id="sidebar" class="app-sidebar sticky sticky-pin">`, `.main-content.app-content > .container-fluid > .row`,
`.scrollToTop`, `#responsive-overlay`, `#floating-alarms-container`, `#floating-notification-container`;
then `<footer id="footer">` as a **sibling of `.page`**.

These are not optional decoration — `plugins/custom.js` dereferences `.scrollToTop` and `#loader` unguarded, and
`plugins/sticky.js` dereferences `.app-header` and `#sidebar` unguarded. Omit one and the shared plugin throws.

`header`, `sidebar`, `footer`, and the `<head>` metadata (`<meta charset>`, `<title>E2EAdmin</title>`) are **empty
in the document and filled at runtime** from `pages/imports/`. There is no per-page `<title>` mechanism.

Every content block is a `.card.custom-card` whose first four children are
`<div class="top-left">`, `top-right`, `bottom-left`, `bottom-right` — empty divs the vendor theme turns into the
HUD corner brackets under `[data-card-style=style1]`. Omitting them yields a visually broken card.

The closing script tail is fixed: SockJS CDN, STOMP CDN, `plugins/sticky.js`, simplebar, apexcharts,
per-page opt-ins, `plugins/custom.js`, `header/header.js`, `sidebar/sidebar.js`, and finally the page's own module.

### The JS module

Every one of the 17 page modules opens with the same 10 lines:

```js
window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
```

then a `$(document).ready` that fires the page's AJAX calls and opens the alarm socket, then a single global
namespace object `let <page>Page = { ... }` holding every callback:

```js
$(document).ready(function()
{
    reposPage.loadReposTableComponent();
    orionCommon.makePostAJAXCall('/api/repositories', null, reposPage.loadReposTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts,
        maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});
```

Callbacks are passed as **unbound references** off the global page object, so `this` is unusable inside them —
reach back through the object name (`reposPage.renderReposTableData()`). Rows are built by string concatenation
and inserted with `.append()`; whole blocks use template literals and `.html()`. Nothing is escaped.

Per-row handlers are registered **inside the same `forEach` that builds the row**, delegated off `$('body')` with
an id built from the row key, so registration order does not matter and DataTables re-paging cannot detach them:

```js
$('body').on('click', '#open-readme-file-content-' + repo.name, function(e) { ... });
```

Note the module globals `stompClient`, `reconnectAttempts`, and `reconnectTimer` are write-only decoration —
`connectToWebsocket` takes the last two **by value** and never writes back, and no module ever reads `stompClient`.
Because these are top-level `let` in classic scripts, two page modules can never be loaded on the same page.

## `orionCommon` (`assets/js/orion.js`)

### Actually used by pages

| Function | Notes |
|---|---|
| `loadStandardComponents(prefix)` | Always called with `''`, which yields root-absolute `/imports/...`. Passing nothing gives `"undefined/imports/..."`. |
| `loadComponent(path, mountElementId[, globalFnName])` | GET, requires a `<template>` root, **appends** (never replaces). 3rd arg is a *string* looked up on `window` — never used. |
| `makeGetAJAXCall(url, onOk[, onFail])` | 56 call sites; the default for reads. |
| `makePostAJAXCall(url, body, onOk[, onFail])` | `body` is `JSON.stringify`'d unconditionally — passing `null` sends the literal `"null"`. Some reads use POST this way. |
| `makePutAJAXCall(url, body, onOk[, onFail])` / `makeDeleteAJAXCall(url, onOk[, onFail])` | |
| `connectToWebsocket(url, topic, attempts, maxAttempts, delayMs, timer, onMessage)` | SockJS + STOMP; heartbeats disabled; returns the client synchronously before connecting. |
| `handleAlarm(message)` | The standard `onMessage`. Dedupes by `message.alarmID` in a plain-object map, forever. |
| `showNotification(title, message, timeoutMs)` | Prepends into `#floating-notification-container`. **`title` is ignored.** `null` timeout = never auto-dismiss. |
| `enablePreloaderForElement(selector, text)` / `disablePreloaderOfElement(selector)` | Take a **full selector including `#`**. |
| `buildApexChartLineChart` / `buildApexCharBarChartWithDataLabels` / `buildApexCharColumnChart` | Note the `Char` misspelling on two of them. Take `'#id'` **with** the hash; return nothing, so re-calling stacks charts. |
| `buildArrayOfRangeOfIntegers(n, min, max)` | Feeds `GaugeChart`'s `meterTicks`. |
| `class GaugeChart(element, params)` + `.init()` | Wraps DevExtreme `dxCircularGauge`; needs `plugins/dx.all.js` + `css/dx.light.css`. |

**Error-handling contract, and it is sharp:** with no `onFail`, a failure raises an `alert()` and throws — twice,
plus an unhandled rejection. With an `onFail`, a non-2xx invokes it **and then falls through to `response.json()`
and the success callback**, so both can fire for one request. `onFail` receives a raw `Response` (on `!ok`) or an
`Error` (on network/abort) — neither has `.data`, so a handler shared between success and failure will throw.
`response.json()` is unconditional, so the backend must always return a JSON body (a 204 lands in `.catch`).
GET and POST abort after 60 s; **PUT and DELETE have no timeout**.

### Present but with zero call sites

`renderDataTable`, `loadTemplateComponent`, `loadTemplates`, `loadComponentDataAsText`, `updateComponent`,
`updateComponentValue`, `uploadFile`, `createFormToDownloadFile`, `bindDownloadButtonAndCreateFormToDownloadFile`,
`getCookie`, `copyToClipboard`, `resetBrowserURL`, `extractRequestParameterFromPageURL`,
`extractAllRequestParameters`, and all four filter helpers (`areAllFiltersValid`, `areAllRequiredFiltersValid`,
`addFilterValuesToDataToSendObject`, `addRequiredFilterValuesToDataToSendObject`).

This matters twice over. First, the pages reimplement several of them locally instead:
all 7 DataTables pages hand-roll `renderDataTable`'s options object (dropping its `1000` length tier), and
`repoDetails.js` / `sequentialPipelineRun.js` each inline a copy of `extractRequestParameterFromPageURL`
— including its bug, a `.endsWith("#")` call three lines *before* the null check, so visiting `/repoDetails`
with no `?repo=` throws instead of alerting. Second, `loadTemplateComponent`/`loadTemplates` describe a
server-side-rendered-template mechanism (POST JSON, get `text/html`, `innerHTML` it, re-host its `<script>`s)
that this backend does not implement — do not assume it works.

`orion.js` also installs a global `$(window).on('resize')` that calls `.columns.adjust()` on every initialised
DataTable. Every table page must call `$(window).trigger('resize')` right after `new DataTable(...)` or the
`scrollX` headers misalign.

## Fragments (`pages/imports/`)

`loadComponent` fetches an HTML file whose root element **must** be `<template>`, clones `template.content`, and
`appendChild`s it into the mount element. Consequences:

- No `<template>` root → `TypeError` → `alert()`. `response.ok` is never checked, so a 404 fails the same way.
- It appends; calling it twice duplicates the markup and its ids.
- Scripts inside a `<template>` **never execute** (they are inert in the fragment and flagged already-started).
- The path is resolved against the **document URL**, and there is no `<base>` tag. Use root-absolute paths.

The four layout fragments (`HTMLHeader.html`, `header.html`, `sidebar.html`, `footer.html`) mount into
`#html-header`, `#header`, `#sidebar`, `#footer`. Note that `header.html` and `sidebar.html` are mostly commented
out: only the logo survives in the header, and only **Home / Configuration / Recent Deployments** survive in the
sidebar (11 nav entries plus the Analytics submenu sit inside one big HTML comment). Because of that,
`header/header.js` and `sidebar/sidebar.js` are both entirely dead code — they only bind to elements that no
longer render. There is no active-link mechanism at all; `orion.css` styles `.side-menu__item.active` but nothing
ever adds that class.

Fragment loading is optional — 9 of the 16 sample pages build all their markup in JS instead.

## Backend contract

- **Envelope:** every response is read as `response.data.<field>`. Analytics endpoints add a second level,
  `response.data.data`, an array of `{first, second}` tuples (Java `Pair` leaking through); `recentDeployments`
  reads `response.data.data.recentDeployments`.
- **Endpoint groups:** `/api/repositories/**` (list, count, details, readme, pipeline-configuration, groups,
  sequences, important, names, deployments, e2e-test-existence, pipelines/commands/**, tests/logs/**),
  `/api/analytics/pipelines/runs/**`, `/api/blames`, `/api/blames/summary`, `/api/blames/calendar`,
  `/api/configuration/types/{repository-provider-api|repository-provider-pipeline|slack|emailer|sla|wiki-provider-api}`,
  `/api/kanban`, `/api/services`, `/api/support-questions`.
- **Query params in use:** `numberOfDays`, `numberOfMinutes`, `numberOfRecentPipelineRuns`, `dataToUse=LIVE`,
  `status`, `environment`, `branch`. Nothing is URL-encoded — a repo name containing `/`, `?`, `#` or a space
  breaks the request.
- **WebSocket:** SockJS at `/websocket`, one STOMP topic `/topic/alarms`, empty connect headers. Message shape is
  `{alarmID, alarmMessage, alarmEventID}`; only the first two are consumed, and the body is **not** enveloped.
  The acknowledge/disable alarm UI and the `#floating-alarms-container` feed are commented out — alarms surface
  only as a transient toast.
- The frontend calls everything through relative `/api/...`, **except** four generated links that hardcode
  `http://localhost:8081/repoDetails?repo=...` (`repos.js`, `services.js`, `recentDeployments.js`,
  `testRunActivity.js`). SockJS and STOMP themselves load from public CDNs on every page.

## Styling

`assets/css/styles.css` (869 KB) is a vendored Bootstrap 5 admin template — its runtime switcher namespaces
everything under `scifi*` localStorage keys and its font is Rajdhani. `assets/css/orion.css` is the only
first-party sheet and its most important line is `--primary-rgb: 0, 255, 255`, which retints the whole vendor
theme from spring-green to cyan. It also hard-pins the sidebar to `12%` (defeating the theme's collapse states),
restyles DevExtreme gauge SVG internals, and adds `.LED-gauge`, `.alert-cyan`, `.hidden`, `.table-text`,
`.table-button`, `.anchor-text`, `.page-title`.

Theme attribute values the vendor CSS actually implements: `data-theme-mode` `dark|light`; `data-nav-layout`
`vertical|horizontal`; `data-vertical-style` `closed|detached|doublemenu|icontext|overlay`; `data-card-style`
`style1`…`style10`; `data-card-background` `background1`…`background9`. **`data-toggled="detached-open"` — the
value every page ships — has zero matching rules and is inert.** `plugins/main.js` can also override any of these
from localStorage at load time.

Icon fonts are bundled in `icons.css`: `ti-` (Tabler) and `fe-` (Feather) and `bx-`/`la-` need their base class
(`ti`, `fe`, `bx`, `la*`); `bi-` (Bootstrap Icons) and `ri-` (Remix) work bare. Only `ti-`, `fe-`, `bi-`, and
`ri-` are used. Several classes in the markup — `btn-rounded`, `fw-600`, `fs-12px`, `pt-5px`, `pb-5px` — have no
definition anywhere and are inherited cargo.

Use `class="hidden"` for JS-toggled visibility; Bootstrap's `d-none` appears zero times in first-party markup.

## Conventions

- 4-space indent, never tabs. **Exactly two blank lines** between sibling HTML blocks, between top-level JS
  statements, and between page-object members; one blank line inside a function body.
- Allman braces in JS (`$(document).ready(function()` then `{` on its own line). Object members are written
  `name : function(response)` with a space before the colon. (`sidebar/sidebar.js` is K&R — do not copy it.)
- Ids are kebab-case, with fixed suffixes: `-area` (JS mount point), `-button`, `-table` / `-table-body`,
  `-modal-body`, `-gauge`; form fields are prefixed `input-`. Bootstrap modal roots are the one exception —
  camelCase ending in `Modal`.
- Chart/stat card bodies carry the literal inline style `class="card-body px-0" style="margin-left:7%"`.
- Never write a `<table>` body in the HTML — the page module builds the rows.

## Adding a new page

1. Copy **`app/public/pages/index.html`** (not any `sample-pages/*.html`) to `app/public/pages/<page>.html`,
   at depth 0 — all asset hrefs are document-relative and only depth 0 resolves.
2. Keep the `<html data-*>` block, `<body id="yapily-admin">`, and all eight shell elements verbatim.
3. Add page-specific libs to the head opt-in slot and to the script tail before `plugins/custom.js`; keep
   `orion.css` as the last `<link>`.
4. Swap the last `<script>` to `assets/js/<page>/<page>.js`.
5. Create `app/public/assets/js/<page>/<page>.js` with the standard 10-line preamble, a `$(document).ready`, and
   a `let <page>Page = {}` namespace.
6. For reusable markup, add `app/public/pages/imports/<page>/<thing>.html` with a `<template>` root and load it
   with a root-absolute path: `orionCommon.loadComponent('/imports/<page>/<thing>.html', '<thing>-area')`.
   **`await` it before any AJAX that writes into it** — the sample modules do not, which is a live race.
7. Add a `<li class="slide">` entry to `pages/imports/sidebar.html`.
8. Nothing to change in `server.js` unless you introduce a new file extension.

## Known breakage

Read this before assuming any sample page runs.

- **Only `/` works.** `index.html` is the sole document at `pages/` root; all 16 real pages live under
  `pages/sample-pages/`. The server has no fallback, so `/repos`, `/configuration`, `/recentDeployments`, and
  every other link in the sidebar and on the landing page 404. Reaching a page at all requires `/sample-pages/<name>`.
- **Sample pages cannot boot even when reached.** Served at `/sample-pages/<name>`, their document-relative
  `assets/...` hrefs resolve to `/sample-pages/assets/...`, which misses the server's `/assets/` branch entirely.
  jQuery, Bootstrap, and `orion.js` never load, so `orionCommon` is undefined.
- **All 16 sample page scripts point at the wrong path** — `assets/js/<page>/<page>.js` while the file sits at
  `assets/js/sample-js/<page>/<page>.js`. Exactly these 16 asset references are broken; every other one resolves.
- **All 7 `sample-imports` fragment paths 404** — they pass `'imports/<page>/<thing>.html'` while the files sit at
  `pages/imports/sample-imports/<page>/<thing>.html`.
- Root cause: commit `81b9a8e` `git mv`'d 39 files into `sample-pages/`, `sample-js/`, and `sample-imports/` with
  no content edits, so every internal reference still describes the pre-move layout. Treat the sample tree as
  **reference material to copy patterns from, not runnable code**. Fixing it means either flattening those three
  folders back up one level or rewriting every reference to be root-absolute.
- Loaded-but-unused vendor deps: `dragula` (kanbanBoard.html has no drag-drop code), FilePond and
  `flatpickr("#targetDate")` in `kanbanBoard.js` (no such element).
- `custom-switcher.min.js` and `defaultmenu.min.js` ship but no page loads them, so there is no theme switcher UI.
