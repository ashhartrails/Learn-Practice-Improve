// Module worker: runs Python tasks with Pyodide. Also importable in Node for tests.

function lastLine(e) {
  const text = String((e && e.message) || e).trim();
  const lines = text.split('\n').filter((l) => l.trim().length > 0);
  return lines.length ? lines[lines.length - 1] : text;
}

export async function runPythonTests(pyodide, code, tests) {
  const out = [];
  pyodide.setStdout({ batched: (s) => { if (out.length < 200) out.push(s); } });
  pyodide.setStderr({ batched: (s) => { if (out.length < 200) out.push(s); } });
  try {
    // Syntax check once, without running anything.
    pyodide.globals.get('compile')(code, '<your code>', 'exec');
  } catch (e) {
    return { tests: [], error: 'Syntax error: ' + lastLine(e), output: '' };
  }
  const results = [];
  for (const t of tests) {
    // A fresh namespace per test, so tests can't affect each other.
    const ns = pyodide.globals.get('dict')();
    ns.set('__name__', '__main__');
    try {
      pyodide.runPython(code, { globals: ns });
      pyodide.runPython(t.code, { globals: ns });
      results.push({ name: t.name, passed: true, message: '' });
    } catch (e) {
      let message = lastLine(e);
      if (message === 'AssertionError') message = 'Assertion failed';
      results.push({ name: t.name, passed: false, message });
    } finally {
      ns.destroy();
    }
  }
  return { tests: results, error: null, output: out.join('\n').slice(0, 4000) };
}

if (typeof WorkerGlobalScope !== 'undefined' && self instanceof WorkerGlobalScope) {
  let ready = null;
  self.onmessage = async (e) => {
    const { id, payload } = e.data;
    let result;
    try {
      if (!ready) {
        const { loadPyodide } = await import('./pyodide/pyodide.mjs');
        ready = loadPyodide({ indexURL: new URL('./pyodide/', self.location.href).href });
      }
      const pyodide = await ready;
      result = await runPythonTests(pyodide, payload.code, payload.tests || []);
    } catch (err) {
      result = { tests: [], error: lastLine(err), output: '' };
    }
    self.postMessage({ id, result });
  };
}
