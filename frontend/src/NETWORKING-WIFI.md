# Openwire: Wi-Fi file transfers

Use this guide to share files directly between computers on the **same trusted Wi-Fi or LAN**. Openwire uses a **UDP tracker** to locate seeders, then transfers file data **directly over TCP**. The tracker does not store the file.

## Before you start

- Install and launch the current Openwire desktop build on both computers. After editing source code, rebuild the desktop app; restarting an older installer will not load new changes.
- Connect both computers to the same LAN. Avoid guest Wi-Fi, VPN routing conflicts, and router **AP/client isolation** (which prevents devices from communicating).
- Choose **one computer to host the tracker**. It must stay on while the other computer discovers peers.
- Default network ports: **UDP 5000** for the tracker and **TCP 7000** for a seeder. The HTTP API is separate and should remain bound to `127.0.0.1`.

## Set up two Windows computers

### 1. Find each computer's Wi-Fi IP address

On each computer, open **Command Prompt**, run:

```powershell
ipconfig
```

Find **Wireless LAN adapter Wi-Fi → IPv4 Address**. For illustration:

| Computer | Role | Example Wi-Fi IPv4 |
|---|---|---|
| PC A | Tracker host and seeder | `192.168.1.25` |
| PC B | Receiver | `192.168.1.40` |

These addresses are **examples**, not values to copy. Use the actual IP addresses shown on your machines. If you have several adapters (WSL, VPN, Ethernet), verify that Openwire's auto-detected peer IP matches the Wi-Fi adapter.

### 2. Configure Node settings on both computers

| Node setting | PC A | PC B |
|---|---|---|
| Tracker host | `192.168.1.25` | `192.168.1.25` |
| Tracker UDP port | `5000` | `5000` |
| Advertised peer IP | `192.168.1.25` | `192.168.1.40` |
| Peer TCP port | `7000` | `7000` |

**Both computers must use the same tracker host IP.** Each computer must advertise **its own** reachable Wi-Fi address. The same TCP port can be used on different computers.

Openwire may start an embedded tracker on each desktop instance by default, but only the tracker at the configured host address is used for discovery. Only one process can bind the tracker port on a given computer. If running the backend manually on PC B, you can disable its unused embedded tracker with `--p2p.tracker.enabled=false`.

### 3. Allow local network traffic through Windows Firewall

Use the **Private** network profile on a trusted home or office LAN. On the tracker computer (PC A), permit inbound **UDP port 5000**. On every computer that shares files, permit inbound **TCP port 7000** (or its configured peer TCP port). Allowing Openwire/Java on the Private profile is another option if you understand which executable is being permitted.

Do **not** expose the desktop app's HTTP API, forward these ports on your router, or disable Windows Firewall entirely. On managed or public networks, ask the network administrator rather than bypassing restrictions.

### 4. Share a file from PC A

1. In Openwire on PC A, add or drop a file into the library.
2. Start sharing/seeding it. In desktop mode, the original file stays at its existing location.
3. Copy the displayed **64-character SHA-256 hash** and send it to the receiver.
4. Leave PC A running and connected to Wi-Fi until the transfer completes.

### 5. Download on PC B

1. Paste the hash into peer search. The results should include PC A's Wi-Fi IP and TCP port.
2. Enter the destination filename **including its original extension**, for example `report.pdf`, `video.mp4`, or `archive.tar.gz`.
3. Start the download and wait for it to complete. Openwire checks the completed file's SHA-256 against the requested hash.
4. In the updated **desktop app**, the completed file is automatically saved in the receiving computer's **Downloads** folder:

   ```text
   C:\Users\<WindowsUsername>\Downloads\
   ```

   This is the receiving computer's Downloads folder, not the sender's. Windows may have redirected Downloads to another location; the desktop app should use the OS-reported folder.

### File names and extensions

The desktop save step preserves the extension **provided in the download filename**. It does not reliably infer an original extension from a SHA-256 hash alone. For example:

| Requested name | Saved file |
|---|---|
| `report.pdf` | `report.pdf` |
| `video.mp4` | `video.mp4` |
| `archive.tar.gz` | `archive.tar.gz` |
| `report.pdf` when it already exists | `report (1).pdf` |

An existing file should not be overwritten; the app chooses an available numbered name. Check the app's download status or notifications if saving fails. A completed network transfer and a successful save to Downloads are distinct operations.

**Browser/API mode is different:** completed downloads are held by the backend until retrieved from `/api/downloads/{id}/file`. With `p2p.keep-downloads=false`, those backend copies are temporary and subject to `p2p.download-ttl-minutes` (default 30). Changing `p2p.keep-downloads` does not by itself move files to Windows Downloads; automatic Downloads-folder saving is a **desktop app** feature.

## Troubleshooting

| Symptom | What to check |
|---|---|
| No peers found | Both apps point to the **same tracker host**; the sender has started seeding; UDP 5000 is permitted on the tracker host; the sender is still online. |
| Tracker did not respond | The host IP and UDP port are correct; PC A is running; only one tracker owns UDP 5000 on PC A; firewall permits inbound UDP. |
| Peer appears but download fails | PC A advertises its actual Wi-Fi IP; TCP 7000 is permitted inbound on PC A; no AP/client isolation or VPN routing interference. |
| Wrong IP shown in Node settings | Compare it with `ipconfig`; manually set the Wi-Fi IPv4 address rather than a WSL, VPN, or virtual adapter IP. |
| Transfer completes but no file appears in Downloads | Confirm you are using the updated **desktop** build, not just the browser UI; check the app's save notification and desktop logs; verify Downloads-folder permissions and free disk space. |
| File is saved without `.pdf`/`.mp4` | Enter the destination filename with its extension **before** downloading. The hash cannot supply the original filename. |
| Duplicate file name | Look for `name (1).ext`, `name (2).ext`, etc., in Downloads. |
| Frontend changes do not appear | Rebuild the frontend and backend/desktop package; checked-in static assets may be stale. See `desktop/README.md`. |

For a basic connectivity check, try `ping <other-computer-ip>` from Command Prompt. A failed ping is **not definitive** because Windows may block ICMP. Check firewall rules and router isolation separately.

## Security and limitations

- The desktop HTTP API should listen only on `127.0.0.1` and use its app token; **do not bind it to `0.0.0.0`** to make file sharing work. Only the UDP tracker and TCP seeder need LAN access.
- File transfers are **not encrypted**, and peers are not authenticated. SHA-256 verifies file integrity, **not confidentiality or sender identity**. Use a trusted network and share hashes through a trusted channel.
- This setup is for reachable devices on the same LAN. It does not provide NAT traversal, Internet-wide discovery, or secure remote sharing.
- The shared-file list is not persistent across app restarts; re-add and re-seed files as needed.
- Both peers need compatible protocol versions and the same `p2p.chunk-size` (default `524288`).

For complete build, API, and configuration instructions, see the root `README.md` and `desktop/README.md`.
