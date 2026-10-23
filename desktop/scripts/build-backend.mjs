// Builds the web UI into the backend, packages the backend as one jar, and copies it to
// desktop/build/backend.jar. Needs Node, Maven and a JDK (17+).
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const desktop = join(dirname(fileURLToPath(import.meta.url)), '..');
const root = join(desktop, '..');
const frontend = join(root, 'frontend');
const backend = join(root, 'backend');

function run(command, args, cwd) {
  console.log(`\n> ${command} ${args.join(' ')}   (in ${cwd})`);
  // .cmd launchers (npm, mvn) need a shell on Windows; elsewhere run directly.
  const windows = process.platform === 'win32';
  const result = windows
    ? spawnSync([command, ...args].join(' '), { cwd, stdio: 'inherit', shell: true })
    : spawnSync(command, args, { cwd, stdio: 'inherit' });
  if (result.error || result.status !== 0) {
    console.error(`\nFailed: ${command} ${args.join(' ')}`);
    if (result.error) console.error(result.error.message);
    process.exit(result.status || 1);
  }
}

if (!existsSync(join(frontend, 'node_modules'))) run('npm', ['install'], frontend);
run('npm', ['run', 'build'], frontend); // writes the UI into backend/src/main/resources/static
run('mvn', ['-B', '-DskipTests', 'clean', 'package'], backend);

const target = join(backend, 'target');
const jar = readdirSync(target).find((f) => f.endsWith('.jar') && !f.endsWith('.original'));
if (!jar) {
  console.error(`No jar found in ${target}`);
  process.exit(1);
}
mkdirSync(join(desktop, 'build'), { recursive: true });
copyFileSync(join(target, jar), join(desktop, 'build', 'backend.jar'));
console.log(`\nBackend ready: desktop/build/backend.jar (${jar})`);
