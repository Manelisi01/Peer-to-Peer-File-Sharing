# Openwire: peer-to-peer file sharing

Share files directly between computers. There is no central file server: a small **tracker**
only records which peers have which file, and the file itself travels straight from peer to
peer. Files are identified by their **SHA-256 hash**, which you give to the person you want to
share with (copy it and send it however you like). Every download is verified against that
hash.

The project has three parts that you can use together or separately:

| Folder | What it is |
|---|---|
| `backend/` | Spring Boot app: REST API, embedded UDP tracker, TCP seeder, downloader |
| `frontend/` | React (Vite) web interface |
| `desktop/` | Electron wrapper that turns the above into a double-click Windows/macOS/Linux app |

## How it works

- **Tracker (UDP, port 5000).** Seeders register `hash -> ip:port` and send a heartbeat every
  30 seconds. Entries expire after 60 seconds without one. Downloaders ask the tracker who has
  a hash.
- **Seeder (TCP).** One TCP port can serve any number of files. Each request starts with the
  32-byte SHA-256 of the file it wants, followed by a chunk number (`-1` asks for the chunk
  count). A seeder ignores requests for hashes it does not share.
- **Downloader.** Asks the tracker for peers, then fetches chunks in parallel (up to 8 at a
  time) from all of them, falls back to another seeder if one fails, and verifies the final
  SHA-256 before finishing.

## Requirements

- Java 17 or newer
- Maven 3.6 or newer
- Node.js 22 or newer and npm
- To build installers: a full **JDK** 17+ (not just a JRE). See [desktop/README.md](desktop/README.md).

## Quick start (development)

Start the backend first and wait until it is up (about 15 seconds), then the frontend.

**Option A: one command** (Linux, macOS or WSL; run from the project root):

```bash
./run.sh              # backend + frontend, Ctrl+C stops both
./run.sh backend      # only the backend
./run.sh frontend     # only the frontend
```

**Option B: two terminals**

```bash
# terminal 1
cd backend
mvn spring-boot:run

# terminal 2
cd frontend
npm install
npm run dev
```

Open `http://127.0.0.1:5173`. The dev server proxies `/api` to the backend on port 8080.
Use `BACKEND_URL=http://127.0.0.1:9000 npm run dev` if the backend runs on another port.

To serve the UI from the backend itself (no Vite needed):

```bash
cd frontend && npm run build        # writes the UI into backend/src/main/resources/static
cd ../backend && mvn package
java -jar target/peer-to-peer-file-sharing-1.0.0.jar
```

Then open `http://127.0.0.1:8080`.

## Desktop app

The desktop app is a normal window (no browser, no command line for users). It starts its own
private backend on a free port, shares files from where they are on disk (nothing is copied),
and stops the backend when it closes.

```powershell
cd desktop
npm install
npm run build:backend     # builds the UI and the backend jar
npm start                 # opens the window
npm run dist              # builds the installer into desktop/dist/
```

Build each installer on its own operating system (use **Windows PowerShell**, not WSL, for the
Windows `.exe`). Full instructions, troubleshooting and log locations are in
[desktop/README.md](desktop/README.md).

## Sharing between computers on the same Wi-Fi

1. Connect both computers to the same trusted Wi-Fi/LAN. Guest networks or AP/client isolation may prevent connections.
2. Choose one computer to host the **UDP tracker** on port `5000`. On that computer, run `ipconfig` (Windows) and find the Wi-Fi adapter's IPv4 address, for example `192.168.1.25`.
3. On **every** computer, open **Node settings** and enter the tracker computer's Wi-Fi IP as **Tracker host** and `5000` as **Tracker UDP port**. Do not use `127.0.0.1` to reach a tracker on another computer.
4. Set **Advertised peer IP** to **each computer's own** reachable Wi-Fi IPv4 address. Openwire attempts to detect a LAN address, but check it if you have multiple adapters, VPNs, or WSL. **Peer TCP port** can be `7000` on each separate computer.
5. Allow inbound **UDP 5000** on the tracker host and inbound **TCP 7000** on each seeding computer in the operating system firewall (private/trusted network profile).
6. On computer A, add a file, start seeding and send its SHA-256 hash to computer B. On B, search for the hash and download the file.

The desktop app's private HTTP API stays bound to `127.0.0.1`. Other computers communicate with the UDP tracker and TCP seeder, **not** the private HTTP API. For more details, see [NETWORKING-WIFI.md](NETWORKING-WIFI.md).

## Where downloads are saved

**Desktop app:** After a transfer finishes and passes SHA-256 verification, Openwire automatically saves a copy into the **receiving computer's standard Downloads folder** (provided the latest desktop download-saving changes have been built). On Windows, that is normally `C:\Users\<YourUsername>\Downloads\`; Electron uses the operating system's configured Downloads folder on macOS and Linux as well.

The filename entered when starting a download is preserved, including its extension. For example, `document.pdf`, `video.mp4` and `archive.tar.gz` retain those suffixes. If a filename already exists, the app chooses an available name such as `document (1).pdf`, rather than deliberately overwriting the existing file. **Enter the correct extension in the destination filename**; the SHA-256 hash does not contain the original filename or extension.

**Web/API mode:** Completed files remain in backend-managed storage until fetched through `GET /api/downloads/{id}/file`, subject to `p2p.keep-downloads` and the download TTL settings described below. This is distinct from the desktop app's automatic save to Downloads.

## Using the app

1. **Add a file.** Drop it on the page. Its SHA-256 appears next to it.
2. **Check your network settings** (the node settings drawer): your **peer IP** must be an
   address other peers can reach, your **peer port** must be open to them, and everyone must use
   the same **tracker host and port**.
3. **Start seeding.** The file is now listed with the tracker.
4. **Send the hash** to the person you want to share with, by any private channel.
5. **They paste the hash**, search for peers, enter a destination filename **with its extension**, and start the download. Progress is shown in the
   activity panel and a download can be cancelled while it is running. The desktop app saves verified completed transfers to the receiving computer's Downloads folder.

In the library, the action next to a file depends on how it was added. In the desktop app it is
**Stop sharing** and your original file is never touched. Files uploaded into the storage folder
(browser mode) show a **delete** action that also removes the stored copy.

## API

All endpoints are under `http://127.0.0.1:8080/api`.

Status and hashing:

```bash
curl http://127.0.0.1:8080/api/status
curl "http://127.0.0.1:8080/api/files/hash?fileName=sample.iso"     # file inside the storage folder
```

Upload a file into the storage folder (multipart form data; returns name, size and hash):

```bash
curl -F "file=@sample.iso" http://127.0.0.1:8080/api/files
```

Start sharing a stored file. The peer TCP port must be reachable by other peers. Any number of
files can share the same port:

```bash
curl -X POST http://127.0.0.1:8080/api/seeders \
  -H "Content-Type: application/json" \
  -d '{"fileName":"sample.iso","peerIp":"127.0.0.1","peerPort":6000,"trackerHost":"127.0.0.1","trackerPort":5000}'
```

List what this node is seeding, and find seeders for a hash:

```bash
curl http://127.0.0.1:8080/api/seeders
curl "http://127.0.0.1:8080/api/peers?hash=SHA256_HASH"
```

Stop sharing:

```bash
curl -X DELETE "http://127.0.0.1:8080/api/seeders?hash=SHA256_HASH"    # stop only; the file is kept
curl -X DELETE "http://127.0.0.1:8080/api/files?fileName=sample.iso"   # stop and delete the stored file
```

Download. The response is HTTP 202 with a job ID:

```bash
curl -X POST http://127.0.0.1:8080/api/downloads \
  -H "Content-Type: application/json" \
  -d '{"targetFile":"copy.iso","fileHash":"SHA256_HASH","trackerHost":"127.0.0.1","trackerPort":5000,"peerIp":"127.0.0.1","peerPort":7000}'

curl http://127.0.0.1:8080/api/downloads/JOB_ID                  # status and progress
curl -X POST http://127.0.0.1:8080/api/downloads/JOB_ID/cancel   # cancel a running download
curl -OJ http://127.0.0.1:8080/api/downloads/JOB_ID/file         # fetch a completed file
```

With `p2p.keep-downloads=false` (the default) a finished backend download is a temporary copy available
from `/file` until its cleanup deadline. With `true`, the backend keeps the file in its storage folder
and can seed it on `peerPort`. **The desktop app saves a separate copy into the user's Downloads folder**
when the transfer completes; this does not require `p2p.keep-downloads=true`.

Desktop app only (these return 403 unless the app has started the backend with its API token):

```
POST /api/files/local      {"path": "/absolute/path"}   hash a file where it is
POST /api/seeders/local    {"path": ..., "peerIp": ..., "peerPort": ..., "trackerHost": ..., "trackerPort": ...}
```

## Configuration

Set these in `backend/src/main/resources/application.properties` or pass them as
`--property=value` arguments.

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | REST API and UI port |
| `server.address` | `127.0.0.1` | Interface the API listens on. Keep it on loopback unless you add your own protection |
| `server.error.include-message` | `always` | Show the real reason in API errors |
| `p2p.storage-dir` | `./data` | Folder for uploaded and kept files |
| `p2p.keep-downloads` | `false` | Keep backend-managed completed downloads and seed them; does not control desktop saves to Downloads |
| `p2p.download-ttl-minutes` | `30` | Minutes a temporary download stays available. Must be a plain number |
| `p2p.chunk-size` | `524288` | Chunk size in bytes. **Must be the same on every peer** |
| `p2p.tracker.enabled` | `true` | Run the embedded UDP tracker |
| `p2p.tracker.udp-port` | `5000` | Tracker UDP port |
| `p2p.tracker.peer-timeout-ms` | `60000` | Drop a peer after this long without a heartbeat |
| `p2p.tracker.cleanup-interval-ms` | `5000` | How often expired peers are removed |
| `p2p.tracker.heartbeat-ms` | `30000` | How often a seeder re-announces itself |
| `p2p.api-token` | empty | Desktop app: shared secret the window must send as `X-P2P-Token` on every `/api` request |
| `p2p.allow-path-sharing` | `false` | Desktop app: share files by path. Only works if `p2p.api-token` is also set |
| `p2p.exit-on-stdin-close` | `false` | Desktop app: exit when the app that started the backend goes away |

Use one tracker per group of peers. Peers that should use someone else's tracker can start with
`--p2p.tracker.enabled=false` and enter that tracker's address in the settings.

## Security notes

- The API listens on `127.0.0.1` only, so other computers cannot call it. Peers connect to the
  seeding TCP port, which is separate.
- In the desktop app the API additionally requires a random per-launch token that only the
  app's own window knows, so other programs and web pages on the same computer cannot use it.
  This matters because sharing by path can otherwise point at any readable file.
- Transfers are **not encrypted**. The SHA-256 check protects integrity, not privacy.

## Troubleshooting

- **`ECONNREFUSED 127.0.0.1:8080` from Vite.** The backend is not up yet. Wait for
  `Started P2pApplication` in the backend log, or use `./run.sh`, which waits for you.
- **Backend stops at startup with a "Failed to convert value ... long" error.** A numeric
  property has stray characters, for example `p2p.download-ttl-minutes=30S`. It must be `30`.
- **`Cannot find module @rollup/rollup-...` or `ENOTEMPTY` during `npm install`.**
  `node_modules` was installed by a different operating system than the one running it (for
  example installed in Windows PowerShell and run in WSL). Delete `node_modules` and install
  again from the terminal you will actually use. Do not mix the two for one project.
- **PowerShell: "running scripts is disabled".** Run
  `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`, or type `npm.cmd`
  instead of `npm`.
- **`mvn` is not recognized.** Install Maven and set `JAVA_HOME`. Programs started before you
  changed environment variables (including VS Code's terminal) must be restarted to see them.
- **Peers cannot connect.** Verify that all peers use the **same tracker host IP**, allow inbound UDP `5000` on the tracker host and TCP `7000` on the seeder, and check the seeder's advertised Wi-Fi IP.
- **Download has no extension.** Enter a destination filename with the desired extension (for example `report.pdf`). A SHA-256 hash does not encode the original filename.

## Known limitations

- **Peers must be reachable.** Across different home networks the seeder's TCP port normally
  has to be forwarded on its router. There is no NAT traversal yet.
- **LAN IP detection is best-effort.** The tracker records the IP a peer reports about itself and does
  not verify it. Confirm the advertised Wi-Fi address in Node settings.
- **The list of shared files is not saved.** After a restart, add and seed your files again.
- **All peers need the same version.** The chunk request format includes the file hash, so older
  builds cannot talk to newer ones, and `p2p.chunk-size` must match everywhere.

## Build and test

```bash
cd backend
mvn test
mvn package
```