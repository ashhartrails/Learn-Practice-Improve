// Classic worker: runs JavaScript and SQL tasks. Also loadable in Node for tests.
'use strict';

function describe(e) {
  if (e && e.message) return String(e.message);
  return String(e);
}

function same(a, b) {
  if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(a), Math.abs(b));
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => same(x, b[i]));
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    const ka = Object.keys(a).sort(), kb = Object.keys(b).sort();
    return same(ka, kb) && ka.every((k) => same(a[k], b[k]));
  }
  return a === b;
}

function runJavaScript(code, tests) {
  const out = [];
  const log = (...args) => out.push(args.map(String).join(' '));
  const assert = (cond, msg) => { if (!cond) throw new Error(msg || 'Assertion failed'); };
  const assertEqual = (actual, expected, msg) => {
    if (!same(actual, expected)) {
      throw new Error((msg ? msg + ': ' : '') + 'expected ' + JSON.stringify(expected) + ', got ' + JSON.stringify(actual));
    }
  };
  const console = { log, info: log, warn: log, error: log };
  // Check it parses first so a syntax error is reported once, not per test.
  try { new Function(code); } catch (e) { return { tests: [], error: 'Syntax error: ' + describe(e), output: '' }; }
  const results = tests.map((t) => {
    try {
      // Fresh run of the learner's code for every test, so tests can't affect each other.
      new Function('assert', 'assertEqual', 'console', '"use strict";\n' + code + '\n;\n' + t.code)(assert, assertEqual, console);
      return { name: t.name, passed: true, message: '' };
    } catch (e) {
      return { name: t.name, passed: false, message: describe(e) };
    }
  });
  return { tests: results, error: null, output: out.join('\n').slice(0, 4000) };
}

function lastResult(db, sql) {
  const res = db.exec(sql);
  if (res.length === 0) return { columns: [], values: [] };
  return res[res.length - 1];
}

function sameRows(a, b, ordered) {
  if (a.length !== b.length) return false;
  if (ordered) return a.every((row, i) => same(row, b[i]));
  const key = (row) => JSON.stringify(row.map((v) => (typeof v === 'number' ? Number(v.toPrecision(12)) : v)));
  const ka = a.map(key).sort(), kb = b.map(key).sort();
  return ka.every((k, i) => k === kb[i]);
}

function preview(r) {
  const head = r.columns.join(' | ');
  const rows = r.values.slice(0, 5).map((v) => v.map((x) => (x === null ? 'NULL' : String(x))).join(' | '));
  return [head].concat(rows).join('\n') + (r.values.length > 5 ? '\n… ' + r.values.length + ' rows' : '');
}

function runSql(SQL, setup, query, reference, ordered) {
  let mine, expected;
  try {
    const ref = new SQL.Database();
    ref.exec(setup);
    expected = lastResult(ref, reference);
    ref.close();
  } catch (e) {
    return { tests: [], error: 'Reference query failed: ' + describe(e), output: '' };
  }
  try {
    const db = new SQL.Database();
    db.exec(setup);
    mine = lastResult(db, query);
    db.close();
  } catch (e) {
    return { tests: [{ name: 'Query runs', passed: false, message: describe(e) }], error: null, output: '' };
  }
  const columnsOk = mine.columns.length === expected.columns.length;
  const rowsOk = columnsOk && sameRows(mine.values, expected.values, ordered);
  return {
    tests: [
      { name: 'Query runs', passed: true, message: '' },
      { name: 'Same number of columns', passed: columnsOk, message: columnsOk ? '' : 'Expected ' + expected.columns.length + ' columns, got ' + mine.columns.length },
      { name: 'Same rows' + (ordered ? ' in the same order' : ''), passed: rowsOk, message: rowsOk ? '' : 'Expected ' + expected.values.length + ' rows, got ' + mine.values.length },
    ],
    error: null,
    output: 'Your result:\n' + preview(mine),
  };
}

if (typeof module !== 'undefined') {
  module.exports = { runJavaScript, runSql };
} else {
  let sqlReady = null;
  self.onmessage = async (e) => {
    const { id, payload } = e.data;
    let result;
    try {
      if (payload.kind === 'sql') {
        if (!sqlReady) {
          importScripts('sqljs/sql-wasm.js');
          sqlReady = initSqlJs({ locateFile: (f) => 'sqljs/' + f });
        }
        const SQL = await sqlReady;
        result = runSql(SQL, payload.setup || '', payload.code, payload.reference || '', !!payload.ordered);
      } else {
        result = runJavaScript(payload.code, payload.tests || []);
      }
    } catch (err) {
      result = { tests: [], error: describe(err), output: '' };
    }
    self.postMessage({ id, result });
  };
}
