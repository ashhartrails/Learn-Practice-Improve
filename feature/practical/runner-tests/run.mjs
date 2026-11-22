// Checks the bundled runners in Node: node feature/practical/runner-tests/run.mjs
// (The app runs the same files in a WebView; Node can't be part of the Gradle build.)
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src/main/assets/runner');
const { runJavaScript, runSql } = require(path.join(dir, 'code-worker.js'));
const { runPythonTests } = await import(path.join(dir, 'py-worker.mjs'));
const { loadPyodide } = await import(path.join(dir, 'pyodide/pyodide.mjs'));
const initSqlJs = require(path.join(dir, 'sqljs/sql-wasm.js'));

const passed = (r) => r.error === null && r.tests.length > 0 && r.tests.every((t) => t.passed);
let count = 0;
const check = (name, fn) => fn().then(() => { count++; console.log('ok  ' + name); });

// --- Python
const py = await loadPyodide({ indexURL: path.join(dir, 'pyodide') + path.sep });
const pyTests = [
  { name: 'adds small numbers', code: 'assert add(2, 3) == 5' },
  { name: 'adds negatives', code: 'assert add(-2, -3) == -5, "negatives"' },
];
await check('python right answer passes', async () => {
  const r = await runPythonTests(py, 'def add(a, b):\n    print("hi")\n    return a + b', pyTests);
  assert.ok(passed(r), JSON.stringify(r));
  assert.match(r.output, /hi/);
});
await check('python wrong answer fails with message', async () => {
  const r = await runPythonTests(py, 'def add(a, b):\n    return a - b', pyTests);
  assert.equal(r.tests[0].passed, false);
  assert.equal(r.tests[0].message, 'Assertion failed');
  assert.equal(r.tests[1].message, 'AssertionError: negatives');
});
await check('python syntax error is reported once', async () => {
  const r = await runPythonTests(py, 'def add(a, b)\n    return a', pyTests);
  assert.match(r.error, /^Syntax error/);
});
await check('python runtime error is per test', async () => {
  const r = await runPythonTests(py, 'def add(a, b):\n    return undefined_name', pyTests);
  assert.match(r.tests[0].message, /NameError/);
});
await check('python tests are isolated', async () => {
  const r = await runPythonTests(py, 'items = []\ndef push(x):\n    items.append(x)\n    return len(items)',
    [{ name: 'a', code: 'assert push(1) == 1' }, { name: 'b', code: 'assert push(1) == 1' }]);
  assert.ok(passed(r), JSON.stringify(r));
});

// --- JavaScript
const jsTests = [
  { name: 'adds', code: 'assertEqual(add(2, 3), 5)' },
  { name: 'arrays', code: 'assertEqual(pair(1), [1, 1])' },
];
await check('js right answer passes', async () => {
  assert.ok(passed(runJavaScript('function add(a,b){return a+b}\nconst pair = (x) => [x, x];', jsTests)));
});
await check('js wrong answer fails with message', async () => {
  const r = runJavaScript('function add(a,b){return a-b}\nconst pair = (x) => [x];', jsTests);
  assert.equal(r.tests[0].message, 'expected 5, got -1');
  assert.equal(r.tests[1].passed, false);
});
await check('js syntax error', async () => {
  assert.match(runJavaScript('function add(a,b){', jsTests).error, /^Syntax error/);
});

// --- SQL
const SQL = await initSqlJs();
const setup = "CREATE TABLE emp(id INTEGER, name TEXT, dept TEXT, salary REAL);" +
  "INSERT INTO emp VALUES (1,'Ana','IT',100),(2,'Bo','IT',80),(3,'Cy','HR',90);";
const reference = "SELECT dept, AVG(salary) FROM emp GROUP BY dept";
await check('sql equivalent query passes (order ignored)', async () => {
  const r = runSql(SQL, setup, "select dept, avg(salary) as a from emp group by dept order by dept desc", reference, false);
  assert.ok(passed(r), JSON.stringify(r));
});
await check('sql wrong query fails', async () => {
  const r = runSql(SQL, setup, "SELECT dept, SUM(salary) FROM emp GROUP BY dept", reference, false);
  assert.equal(r.tests[2].passed, false);
});
await check('sql order matters when ordered', async () => {
  const ref = "SELECT name FROM emp ORDER BY salary DESC";
  assert.ok(passed(runSql(SQL, setup, "SELECT name FROM emp ORDER BY salary DESC", ref, true)));
  assert.equal(runSql(SQL, setup, "SELECT name FROM emp ORDER BY salary", ref, true).tests[2].passed, false);
});
await check('sql error is reported', async () => {
  const r = runSql(SQL, setup, "SELEC nonsense", reference, false);
  assert.equal(r.tests[0].passed, false);
});
console.log(`\n${count} runner checks passed`);
