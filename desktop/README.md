# Openwire desktop app

A native window around the app. It starts its own private backend on a free port of this
computer, so every user runs their own node and nothing is exposed to the network except the
seeding port peers connect to.

What the app does at launch:

- picks a free port and starts the backend on `127.0.0.1` only;
- gives the backend a random secret that only its own window knows (other programs and web
  pages cannot use the local API);
- shares files from where they are (no copy is made); "delete" on such a file only stops
  sharing it and never touches the original;
- stores its own data in the user's app-data folder and stops the backend when it closes
  (also if the app crashes).

## Run it during development

Needs Node 18+, Maven and Java 17+.

```bash
cd desktop
npm install
npm run build:backend   # builds the UI + backend jar into desktop/build/backend.jar
npm start               # opens the desktop window (uses the Java on your PATH)
```

## Build an installer

Needs Node 18+, Maven and a **JDK 21+** (a JRE is not enough, `jlink` comes with the JDK).
Make sure `JAVA_HOME` points at that JDK.

```bash
cd desktop
npm install
npm run dist
```

`npm run dist` builds the UI and backend, creates a small bundled Java runtime with `jlink`
(and starts the real backend with it as a check, failing the build if a Java module is
missing), then packages the installer into `desktop/dist/`.

A bundled runtime only works on the operating system it was built on, so build each
installer on its own OS: run the command in **Windows PowerShell** for the Windows `.exe`
(not inside WSL, which produces a Linux build), on a Mac for the `.dmg`, on Linux for the
`.AppImage`.

The installers are not code-signed: Windows SmartScreen and macOS Gatekeeper will warn on
first launch until you sign them.

## If something goes wrong

The backend writes its log to `backend.log` in the app's data folder:

- Windows: `%APPDATA%\Openwire\logs\backend.log`
- macOS: `~/Library/Application Support/Openwire/logs/backend.log`
- Linux: `~/.config/Openwire/logs/backend.log`

If the build fails with a missing Java module, add it to `MODULES` in
`scripts/build-runtime.mjs`.
