// Page side: owns the workers, enforces time limits, keeps results for the app to poll.
'use strict';
const results = {};
const workers = { py: null, code: null };

function worker(kind) {
  if (!workers[kind]) {
    workers[kind] = kind === 'py'
      ? new Worker('py-worker.mjs', { type: 'module' })
      : new Worker('code-worker.js');
  }
  return workers[kind];
}

function killWorker(kind) {
  if (workers[kind]) { workers[kind].terminate(); workers[kind] = null; }
}

// payload: { kind: python|javascript|sql, code, tests:[{name, code}], setup, reference, ordered, timeoutMs }
function startRun(id, payloadJson) {
  const payload = JSON.parse(payloadJson);
  const kind = payload.kind === 'python' ? 'py' : 'code';
  const w = worker(kind);
  // Python needs time to start the first time; the app passes a longer limit for that.
  const timer = setTimeout(() => {
    killWorker(kind);
    results[id] = JSON.stringify({ tests: [], error: 'Your code took too long. Check for an endless loop.', output: '', timedOut: true });
  }, payload.timeoutMs || 15000);
  const onMessage = (e) => {
    if (e.data.id !== id) return;
    clearTimeout(timer);
    w.removeEventListener('message', onMessage);
    results[id] = JSON.stringify(e.data.result);
  };
  w.addEventListener('message', onMessage);
  w.postMessage({ id, payload });
  return 'started';
}

function takeResult(id) {
  const r = results[id];
  if (r === undefined) return null;
  delete results[id];
  return r;
}
