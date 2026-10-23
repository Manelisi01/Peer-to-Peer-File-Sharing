// Generates the app icon from the app's own code: it loads the built UI, takes the real
// ".brand-mark" element from the top bar (the green tile with the network glyph) and
// captures it at each icon size, so the icon always matches what the window shows.
//
//   1. cd frontend && npm run build          (builds the UI the icon is taken from)
//   2. cd desktop  && npm run icons          (writes desktop/assets/icon.png and icon.ico)
//
// Optional: ICON_UI_DIR=<folder with a built UI> to use a different build.
const { app, BrowserWindow } = require('electron');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const UI_DIR = process.env.ICON_UI_DIR || path.resolve(__dirname, '../../backend/src/main/resources/static');
const OUT_DIR = path.resolve(__dirname, '../assets');
const ICO_SIZES = [16, 24, 32, 48, 64, 128, 256]; // .ico frames (256 is the maximum)
const PNG_SIZE = 1024;                           // icon.png (used for Linux/macOS)
const MARK_PX = 30;                              // .brand-mark is 30x30 CSS px in the top bar

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };

function serveUi() {
  const server = http.createServer((req, res) => {
    const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    const file = path.join(UI_DIR, rel === '/' ? 'index.html' : rel);
    if (!file.startsWith(UI_DIR) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

// One normal-sized window is reused for every size (tiny windows are not reliable); the real
// element is scaled up/down inside it and the matching region is captured.
async function openStage(port) {
  const win = new BrowserWindow({
    width: PNG_SIZE, height: PNG_SIZE, useContentSize: true, frame: false, transparent: true,
    show: false, webPreferences: { sandbox: true },
  });
  await win.loadURL(`http://127.0.0.1:${port}/`);
  await win.webContents.executeJavaScript(`new Promise((ok, fail) => {
    const t0 = Date.now();
    (function wait() {
      const mark = document.querySelector('.brand-mark');
      if (!mark) return Date.now() - t0 > 15000 ? fail(new Error('.brand-mark not found - is the UI built?')) : setTimeout(wait, 50);
      const stage = document.createElement('div');
      stage.id = 'icon-stage';
      stage.appendChild(mark.cloneNode(true));   // the real element, styled by the app's own CSS
      const style = document.createElement('style');
      style.textContent = 'html,body{margin:0!important;background:transparent!important;overflow:hidden!important}' +
        'body>*:not(#icon-stage){display:none!important}' +
        '#icon-stage{position:fixed;left:0;top:0;width:${MARK_PX}px;height:${MARK_PX}px;transform-origin:0 0}';
      document.head.appendChild(style); document.body.appendChild(stage);
      ok();
    })();
  })`);
  return win;
}

async function captureMark(win, size) {
  await win.webContents.executeJavaScript(`new Promise((ok) => {
    document.getElementById('icon-stage').style.transform = 'scale(${size / MARK_PX})';
    requestAnimationFrame(() => requestAnimationFrame(() => ok()));
  })`);
  await new Promise((r) => setTimeout(r, 250));
  const image = await win.webContents.capturePage({ x: 0, y: 0, width: size, height: size });
  const got = image.getSize();
  if (got.width !== size || got.height !== size) throw new Error(`captured ${got.width}x${got.height}, expected ${size}x${size}`);
  return image.toPNG();
}

// An .ico is a small header + directory followed by the PNG images.
function buildIco(frames) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2);                 // type: icon
  header.writeUInt16LE(frames.length, 4);
  let offset = 6 + 16 * frames.length;
  const entries = frames.map(({ size, png }) => {
    const e = Buffer.alloc(16);
    e.writeUInt8(size >= 256 ? 0 : size, 0);  // 0 means 256
    e.writeUInt8(size >= 256 ? 0 : size, 1);
    e.writeUInt16LE(1, 4);                    // colour planes
    e.writeUInt16LE(32, 6);                   // bits per pixel
    e.writeUInt32LE(png.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += png.length;
    return e;
  });
  return Buffer.concat([header, ...entries, ...frames.map((f) => f.png)]);
}

app.disableHardwareAcceleration();
app.whenReady().then(async () => {
  try {
    if (!fs.existsSync(path.join(UI_DIR, 'index.html'))) throw new Error(`No built UI in ${UI_DIR}. Run "npm run build" in the frontend folder first.`);
    const server = await serveUi();
    const port = server.address().port;
    fs.mkdirSync(OUT_DIR, { recursive: true });

    const win = await openStage(port);
    const frames = [];
    for (const size of ICO_SIZES) frames.push({ size, png: await captureMark(win, size) });
    fs.writeFileSync(path.join(OUT_DIR, 'icon.ico'), buildIco(frames));
    fs.writeFileSync(path.join(OUT_DIR, 'icon.png'), await captureMark(win, PNG_SIZE));
    console.log(`Wrote icon.ico (${ICO_SIZES.join(', ')} px) and icon.png (${PNG_SIZE} px) to ${OUT_DIR}`);
    server.close();
    app.exit(0);
  } catch (error) {
    console.error(error.message);
    app.exit(1);
  }
});
