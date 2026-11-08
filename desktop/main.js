'use strict';
const { app, BrowserWindow, dialog, session, shell, ipcMain } = require('electron');
const { spawn } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');

const TOKEN_HEADER = 'X-P2P-Token';
const START_TIMEOUT_MS = 120000;

// Random secret for this launch. Only this app's window knows it, so other programs (and web
// pages in a browser) cannot use the local API. See ApiTokenFilter in the backend.
const token = crypto.randomBytes(32).toString('hex');

let backend = null;
let mainWindow = null;
let quitting = false;
let logPath = '';
let backendOrigin = '';


// ---------------------------------------------------------------- backend process

function resolveBackend() {
  if (app.isPackaged) {
    const exe = process.platform === 'win32' ? 'java.exe' : 'java';
    return {
      java: path.join(process.resourcesPath, 'runtime', 'bin', exe),
      jar: path.join(process.resourcesPath, 'backend.jar'),
    };
  }
  // Development (`npm start`): system Java and the jar made by `npm run build:backend`.
  return {
    java: process.env.P2P_JAVA || 'java',
    jar: process.env.P2P_JAR || path.join(__dirname, 'build', 'backend.jar'),
  };
}

// Asks the OS for a free port on the loopback interface.
function freePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.unref();
    probe.on('error', reject);
    probe.listen(0, '127.0.0.1', () => {
      const { port } = probe.address();
      probe.close(() => resolve(port));
    });
  });
}

function launchBackend(port) {
  const { java, jar } = resolveBackend();
  if (!fs.existsSync(jar)) {
    throw new Error(`Backend not found at ${jar}. Run "npm run build:backend" first.`);
  }

  const userData = app.getPath('userData');
  const dataDir = path.join(userData, 'data');
  const logsDir = path.join(userData, 'logs');
  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(logsDir, { recursive: true });
  logPath = path.join(logsDir, 'backend.log');
  const log = fs.openSync(logPath, 'a');

  const child = spawn(java, [
    '-jar', jar,
    `--server.port=${port}`,
    '--server.address=127.0.0.1',
    `--p2p.storage-dir=${dataDir}`,
  ], {
    // The token goes in the environment, not the command line, so it is not visible in
    // process listings. stdin stays open so the backend notices if this app disappears.
    env: {
      ...process.env,
      P2P_API_TOKEN: token,
      P2P_ALLOW_PATH_SHARING: 'true',
      P2P_EXIT_ON_STDIN_CLOSE: 'true',
    },
    stdio: ['pipe', log, log],
    windowsHide: true,
  });
  child.on('error', (error) => { child.spawnError = error; });
  return child;
}

function waitForBackend(port, child) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + START_TIMEOUT_MS;
    let finished = false;
    const done = (fn, value) => { if (!finished) { finished = true; fn(value); } };

    child.once('exit', (code) => done(reject, new Error(`The background service stopped while starting (exit code ${code}).`)));
    child.once('error', (error) => done(reject, new Error(`Could not start Java: ${error.message}`)));

    const poll = () => {
      if (finished) return;
      const retry = () => {
        if (finished) return;
        if (Date.now() > deadline) done(reject, new Error('The background service did not start in time.'));
        else setTimeout(poll, 300);
      };
      const req = http.get({
        host: '127.0.0.1', port, path: '/api/status', timeout: 1500,
        headers: { [TOKEN_HEADER]: token },
      }, (res) => {
        res.resume();
        if (res.statusCode === 200) done(resolve);
        else retry();
      });
      req.on('timeout', () => req.destroy());
      req.on('error', retry);
    };
    poll();
  });
}

function stopBackend() {
  if (!backend || backend.exitCode !== null || backend.signalCode !== null) return;
  try { backend.stdin.end(); } catch { /* already closed */ }
  try { backend.kill(); } catch { /* already gone */ }
}

// Save completed transfers directly to the receiving computer's Downloads folder.
// Electron's download manager streams the file; it does not load it into JS memory.
function installDownloadHandler() {
  ipcMain.handle('openwire:save-download', async (event, id, suggestedName) => {
    if (!mainWindow || event.sender !== mainWindow.webContents) throw new Error('Unauthorized window');
    if (typeof id !== 'string' || !/^[a-zA-Z0-9-]{1,100}$/.test(id)) throw new Error('Invalid download ID');
    if (typeof suggestedName !== 'string') throw new Error('Invalid filename');
    const name = path.basename(suggestedName.replace(/[\\/]/g, '_')).replace(/[<>:"|?*\x00-\x1f]/g, '_').replace(/[. ]+$/, '');
    if (!name || name === '.' || name === '..') throw new Error('Invalid filename');
    const folder = app.getPath('downloads');
    fs.mkdirSync(folder, { recursive: true });
    // Keep the complete original extension, including compound suffixes such as .tar.gz.
    // Insert collision numbers before the suffix rather than after it.
    const compound = name.match(/^(.*?)(\.(?:tar\.(?:gz|bz2|xz|zst)|[a-zA-Z0-9]+))$/i);
    const ext = compound ? compound[2] : '';
    const stem = ext ? name.slice(0, -ext.length) : name;
    let destination = path.join(folder, name);
    let index = 1;
    while (fs.existsSync(destination)) {
      destination = path.join(folder, `${stem} (${index++})${ext}`);
    }
    const url = `${backendOrigin}/api/downloads/${encodeURIComponent(id)}/file`;
    return new Promise((resolve, reject) => {
      const ses = session.defaultSession;
      const timeout = setTimeout(() => { cleanup(); reject(new Error('Download did not start')); }, 15000);
      const cleanup = () => { clearTimeout(timeout); ses.removeListener('will-download', onDownload); };
      const onDownload = (_event, item) => {
        if (item.getURL() !== url) return;
        cleanup();
        item.setSavePath(destination);
        item.once('done', (_event, state) => {
          if (state === 'completed') resolve(destination);
          else reject(new Error(`Saving file ${state}`));
        });
      };
      ses.on('will-download', onDownload);
      mainWindow.webContents.downloadURL(url);
    });
  });
}

// ---------------------------------------------------------------- window

function openExternal(url) {
  if (/^https?:\/\//i.test(url)) shell.openExternal(url);
}

function installRequestHooks(origin) {
  // Attach the secret to every request the window makes to its own backend (API calls,
  // downloads and all), so the UI code does not need to know about it.
  session.defaultSession.webRequest.onBeforeSendHeaders({ urls: [`${origin}/*`] }, (details, callback) => {
    details.requestHeaders[TOKEN_HEADER] = token;
    callback({ requestHeaders: details.requestHeaders });
  });
  // The UI only ever needs to copy a hash to the clipboard.
  const allowed = (permission) => permission === 'clipboard-sanitized-write';
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => callback(allowed(permission)));
  session.defaultSession.setPermissionCheckHandler((_wc, permission) => allowed(permission));
}

function lockDownNavigation(win, origin) {
  win.webContents.setWindowOpenHandler(({ url }) => { openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(origin)) { event.preventDefault(); openExternal(url); }
  });
}

// Window/taskbar icon (the installer embeds its own copy in the .exe; this covers `npm start`
// and Linux).
function appIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icon.png')
    : path.join(__dirname, 'assets', 'icon.png');
}

function createWindow() {
  mainWindow = new BrowserWindow({
    icon: appIconPath(),
    width: 1280,
    height: 860,
    minWidth: 900,
    minHeight: 600,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#f3f5f1',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('closed', () => { mainWindow = null; });
  mainWindow.loadFile(path.join(__dirname, 'loading.html'));
}

function fail(error) {
  if (quitting) return;
  quitting = true;
  stopBackend();
  dialog.showErrorBox(
    'Openwire could not start',
    `${error.message}${logPath ? `\n\nDetails are in:\n${logPath}` : ''}`);
  app.exit(1);
}

async function start() {
  createWindow();
  try {
    const port = await freePort();
    backend = launchBackend(port);
    await waitForBackend(port, backend);

    backend.on('exit', (code) => {
      if (!quitting) fail(new Error(`The background service stopped unexpectedly (exit code ${code}).`));
    });

    const origin = `http://127.0.0.1:${port}`;
    backendOrigin = origin;
    installRequestHooks(origin);
    installDownloadHandler();
    if (mainWindow) {
      lockDownNavigation(mainWindow, origin);
      await mainWindow.loadURL(origin);
    }
  } catch (error) {
    fail(error);
  }
}

// ---------------------------------------------------------------- app lifecycle

if (!app.requestSingleInstanceLock()) {
  // Already running: the seeding port is taken, so a second copy could not work anyway.
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => { quitting = true; stopBackend(); });
  process.on('exit', stopBackend);
  app.whenReady().then(start);
}
