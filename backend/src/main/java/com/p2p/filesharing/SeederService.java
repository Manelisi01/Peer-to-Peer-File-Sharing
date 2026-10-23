package com.p2p.filesharing;

import jakarta.annotation.PreDestroy;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SeederService {

    private static final Logger log = LoggerFactory.getLogger(SeederService.class);
    private static final int HASH_BYTES = 32;
    private static final int REQUEST_TIMEOUT_MS = 10000;

    @Value("${p2p.chunk-size:524288}")
    private int chunkSize;

    // Files inside this folder are copies the app manages itself; anything else is shared
    // in place from wherever the user keeps it and must never be deleted by the app.
    @Value("${p2p.storage-dir:./data}")
    private String storageDirectory = "./data";

    // One TCP listener per port. Any number of files can be shared on the same port:
    // every request starts with the file's SHA-256, which selects the file to serve.
    private final Map<Integer, PortListener> listeners = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newCachedThreadPool();

    public synchronized SharingInfo startSharing(
            Path file,
            String peerIp,
            int peerPort,
            String trackerHost,
            int trackerPort) throws IOException {
        peerIp = validatePeerIp(peerIp);
        if (peerPort < 1 || peerPort > 65535 || trackerPort < 1 || trackerPort > 65535) {
            throw new IllegalArgumentException("Peer and tracker ports must be between 1 and 65535.");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + file);
        }
        Path normalizedFile = file.toAbsolutePath().normalize();

        long fileSize = Files.size(file);
        long count = fileSize == 0 ? 0 : (fileSize + chunkSize - 1) / chunkSize;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File is too large for the peer protocol.");
        }
        String hash = FileHasher.calculateSha256(file).toLowerCase(Locale.ROOT);

        // If this path is already shared but its content has changed, the old entry is stale
        // and would serve the wrong bytes, so drop it first.
        for (PortListener listener : List.copyOf(listeners.values())) {
            for (SharedFile shared : List.copyOf(listener.files.values())) {
                if (shared.file.equals(normalizedFile) && !shared.fileHash.equals(hash)) {
                    removeShared(listener, shared);
                }
            }
        }

        PortListener listener = listeners.get(peerPort);
        if (listener != null) {
            SharedFile existing = listener.files.get(hash);
            if (existing != null) {
                if (existing.file.equals(normalizedFile)) {
                    return existing.info;
                }
                throw new IllegalArgumentException("A file with identical content ("
                        + existing.info.fileName() + ") is already shared on port " + peerPort + ".");
            }
        } else {
            listener = new PortListener(peerPort, new ServerSocket(peerPort));
            listeners.put(peerPort, listener);
            PortListener started = listener;
            workers.submit(() -> acceptClients(started));
        }

        boolean managed = normalizedFile.startsWith(
                Paths.get(storageDirectory).toAbsolutePath().normalize());
        SharingInfo info = new SharingInfo(normalizedFile.getFileName().toString(), hash,
                peerIp, peerPort, fileSize, (int) count, managed);
        SharedFile shared = new SharedFile(normalizedFile, fileSize, (int) count,
                trackerHost, trackerPort, peerIp, hash, info);
        listener.files.put(hash, shared);
        sendTrackerMessage(shared, "REGISTER");
        log.info("Sharing {} as {}:{} ({} chunks, {} file(s) on this port)",
                normalizedFile, peerIp, peerPort, count, listener.files.size());
        return info;
    }

    public int getSharedFileCount() {
        return listeners.values().stream().mapToInt(l -> l.files.size()).sum();
    }

    public List<SharingInfo> getSharedFiles() {
        return listeners.values().stream()
                .flatMap(l -> l.files.values().stream())
                .map(SharedFile::info)
                .toList();
    }

    public synchronized void stopSharing(Path file) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        for (PortListener listener : List.copyOf(listeners.values())) {
            for (SharedFile shared : List.copyOf(listener.files.values())) {
                if (shared.file.equals(normalizedFile)) {
                    removeShared(listener, shared);
                }
            }
        }
    }

    /** Stops sharing a file by its SHA-256 without touching the file. Returns how many shares ended. */
    public synchronized int stopSharingByHash(String fileHash) {
        String hash = fileHash.toLowerCase(Locale.ROOT);
        int stopped = 0;
        for (PortListener listener : List.copyOf(listeners.values())) {
            SharedFile shared = listener.files.get(hash);
            if (shared != null) {
                removeShared(listener, shared);
                stopped++;
            }
        }
        return stopped;
    }

    // Must be called with the service lock held. Closes the port once its last file is gone.
    private void removeShared(PortListener listener, SharedFile shared) {
        if (!listener.files.remove(shared.fileHash, shared)) {
            return;
        }
        sendTrackerMessage(shared, "UNREGISTER");
        log.info("Stopped sharing {} on port {}", shared.file, listener.port);
        if (listener.files.isEmpty() && listeners.remove(listener.port, listener)) {
            try {
                listener.server.close();
            } catch (IOException e) {
                log.warn("Could not close seeder port {}", listener.port, e);
            }
        }
    }

    @Scheduled(fixedRateString = "${p2p.tracker.heartbeat-ms:30000}")
    void sendHeartbeats() {
        listeners.values().forEach(l ->
                l.files.values().forEach(shared -> sendTrackerMessage(shared, "UPDATE")));
    }

    @PreDestroy
    void stop() {
        listeners.values().forEach(listener -> {
            try {
                listener.server.close();
            } catch (IOException e) {
                log.debug("Error closing seeder socket", e);
            }
        });
        workers.shutdownNow();
    }

    private void acceptClients(PortListener listener) {
        while (!listener.server.isClosed()) {
            try {
                Socket client = listener.server.accept();
                workers.submit(() -> serveChunkRequest(client, listener));
            } catch (IOException e) {
                if (!listener.server.isClosed()) {
                    log.warn("Seeder accept failed on port {}", listener.port, e);
                }
            }
        }
    }

    // Request format: 32-byte SHA-256 of the file, then an int chunk number
    // (-1 asks for the total chunk count). Unknown hashes are dropped without a reply.
    private void serveChunkRequest(Socket socket, PortListener listener) {
        try (socket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(REQUEST_TIMEOUT_MS);
            byte[] hashBytes = new byte[HASH_BYTES];
            input.readFully(hashBytes);
            SharedFile shared = listener.files.get(HexFormat.of().formatHex(hashBytes));
            if (shared == null) {
                return;
            }
            int chunkNumber = input.readInt();
            if (chunkNumber == -1) {
                output.writeInt(shared.totalChunks);
                output.flush();
                return;
            }
            if (chunkNumber < 0 || chunkNumber >= shared.totalChunks) {
                return;
            }
            long offset = (long) chunkNumber * chunkSize;
            int length = (int) Math.min(chunkSize, shared.fileSize - offset);
            byte[] buffer = new byte[length];
            try (RandomAccessFile raf = new RandomAccessFile(shared.file.toFile(), "r")) {
                raf.seek(offset);
                raf.readFully(buffer);
            }
            output.write(buffer);
            output.flush();
        } catch (IOException e) {
            log.debug("Failed to serve a chunk request on port {}", listener.port, e);
        }
    }

    private void sendTrackerMessage(SharedFile shared, String command) {
        String message = command + "|" + shared.fileHash + "|" + shared.peerIp
                + "|" + shared.info.peerPort();
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress tracker = InetAddress.getByName(shared.trackerHost);
            socket.send(new DatagramPacket(bytes, bytes.length, tracker, shared.trackerPort));
        } catch (IOException e) {
            log.warn("Could not send {} message to tracker {}:{}", command,
                    shared.trackerHost, shared.trackerPort, e);
        }
    }

    private static String validatePeerIp(String peerIp) throws IOException {
        InetAddress address = InetAddress.getByName(peerIp);
        if (!(address instanceof Inet4Address)) {
            throw new IllegalArgumentException("Only IPv4 peer addresses are supported.");
        }
        return address.getHostAddress();
    }

    public record SharingInfo(
            String fileName,
            String fileHash,
            String peerIp,
            int peerPort,
            long fileSize,
            int totalChunks,
            boolean managed) {
    }

    private record SharedFile(
            Path file,
            long fileSize,
            int totalChunks,
            String trackerHost,
            int trackerPort,
            String peerIp,
            String fileHash,
            SharingInfo info) {
    }

    private static final class PortListener {
        final int port;
        final ServerSocket server;
        final Map<String, SharedFile> files = new ConcurrentHashMap<>();

        PortListener(int port, ServerSocket server) {
            this.port = port;
            this.server = server;
        }
    }
}
