# Offline code runners

Loaded by `WebCodeRunner` in a WebView with no network access (every request outside
`https://appassets.androidplatform.net/` is refused, and the page sets a strict CSP).

| Folder | Package | Version | Licence | Source |
| --- | --- | --- | --- | --- |
| `pyodide/` | Pyodide (CPython 3.14 in WebAssembly), core files only | 314.0.7 | MPL-2.0 | npm `pyodide` |
| `sqljs/` | sql.js (SQLite in WebAssembly) | 1.16.0 | MIT (see `sqljs/LICENSE`) | npm `sql.js` |

To update: download the npm tarball, copy the same file names, and run
`node feature/practical/runner-tests/run.mjs` to check the runners still work.

Protocol: the app calls `startRun(id, jsonPayload)` and then polls `takeResult(id)`, which
returns a JSON string once the run is finished (or `null` while it is running).
