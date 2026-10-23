// Builds a small private Java runtime (desktop/build/runtime) with jlink so the installed app
// does not need Java on the user's machine, then starts the real backend with it as a check.
// Needs a JDK 17+ (for jlink) and desktop/build/backend.jar (run build:backend first).
//
// A runtime only works on the operating system it was built on, so build the Windows
// installer on Windows, the macOS one on a Mac, and the Linux one on Linux.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import http from 'node:http';
import net from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const desktop = join(dirname(fileURLToPath(import.meta.url)), '..');
const jar = join(desktop, 'build', 'backend.jar');
const out = join(desktop, 'build', 'runtime');
const exe = (name) => (process.platform === 'win32' ? `${name}.exe` : name);

// Java modules a Spring Boot web app needs. If the smoke test below ever reports a
// NoClassDefFoundError / "module not found", add the missing module here.
const MODULES = [
  'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging',
  'java.management', 'java.naming', 'java.net.http', 'java.prefs', 'java.rmi',
  'java.scripting', 'java.security.jgss', 'java.security.sasl', 'java.sql', 'java.xml',
  'jdk.crypto.ec', 'jdk.unsupported', 'jdk.zipfs',
].join(',');

if (!existsSync(jar)) {
  console.error('desktop/build/backend.jar is missing. Run "npm run build:backend" first.');
  process.exit(1);
}

const jlink = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', exe('jlink')) : exe('jlink');
// jlink's compression option was renamed in JDK 21 ("2" became "zip-6").
function jlinkMajorVersion() {
  const r = spawnSync(jlink, ['--version'], { encoding: 'utf8' });
  const match = /^\s*(\d+)/.exec(`${r.stdout || ''}${r.stderr || ''}`);
  return match ? Number(match[1]) : 0;
}
const major = jlinkMajorVersion();
const compress = major > 0 && major < 21 ? '2' : 'zip-6';

rmSync(out, { recursive: true, force: true });
console.log(`> ${jlink} (building the bundled Java runtime, JDK ${major || 'unknown'})`);
const built = spawnSync(jlink, [
  '--add-modules', MODULES,
  '--strip-debug', '--no-header-files', '--no-man-pages',
  `--compress=${compress}`,
  '--output', out,
], { stdio: 'inherit' });
if (built.error || built.status !== 0) {
  console.error('\njlink failed. Install a full JDK 17+ (not just a JRE) and/or set JAVA_HOME to it.');
  if (built.error) console.error(built.error.message);
  process.exit(built.status || 1);
}

const freePort = () => new Promise((resolve, reject) => {
  const s = net.createServer();
  s.on('error', reject);
  s.listen(0, '127.0.0.1', () => { const { port } = s.address(); s.close(() => resolve(port)); });
});
const isUp = (port) => new Promise((resolve) => {
  const req = http.get({ host: '127.0.0.1', port, path: '/api/status', timeout: 1500 }, (res) => {
    res.resume();
    resolve(res.statusCode === 200);
  });
  req.on('timeout', () => req.destroy());
  req.on('error', () => resolve(false));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function smokeTest() {
  console.log('\n> smoke test: starting the real backend with the bundled runtime...');
  const port = await freePort();
  const data = mkdtempSync(join(tmpdir(), 'openwire-smoke-'));
  const child = spawn(join(out, 'bin', exe('java')), [
    '-jar', jar, `--server.port=${port}`, '--server.address=127.0.0.1', `--p2p.storage-dir=${data}`,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });

  let output = '';
  child.stdout.on('data', (d) => { output += d; });
  child.stderr.on('data', (d) => { output += d; });
  let exited = false;
  child.on('exit', () => { exited = true; });

  const deadline = Date.now() + 120000;
  let ok = false;
  while (!exited && Date.now() < deadline) {
    if (await isUp(port)) { ok = true; break; }
    await sleep(500);
  }
  child.kill();
  rmSync(data, { recursive: true, force: true });
  if (!ok) console.error(`\nSmoke test FAILED. Last backend output:\n${output.slice(-4000)}`);
  return ok;
}

if (!(await smokeTest())) process.exit(1);
console.log('\nBundled runtime OK: desktop/build/runtime');
