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
import java.util.List;
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

    @Value("${p2p.chunk-size:524288}")
    private int chunkSize;

    private final Map<Integer, SharedFile> sharedFiles = new ConcurrentHashMap<>();
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
        SharedFile existing = sharedFiles.get(peerPort);
        if (existing != null) {
            if (existing.file.equals(file.toAbsolutePath().normalize())) {
                return existing.info;
            }
            throw new IllegalArgumentException("Peer port is already in use: " + peerPort);
        }

        long fileSize = Files.size(file);
        long count = fileSize == 0 ? 0 : (fileSize + chunkSize - 1) / chunkSize;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File is too large for the peer protocol.");
        }
        String hash = FileHasher.calculateSha256(file);
        ServerSocket server = new ServerSocket(peerPort);
        Path normalizedFile = file.toAbsolutePath().normalize();
        SharingInfo info = new SharingInfo(normalizedFile.getFileName().toString(), hash,
                peerIp, peerPort, fileSize, (int) count);
        SharedFile shared = new SharedFile(normalizedFile, fileSize, (int) count,
                server, trackerHost, trackerPort, peerIp, hash, info);
        sharedFiles.put(peerPort, shared);
        workers.submit(() -> acceptClients(shared));
        sendTrackerMessage(shared, "REGISTER");
        log.info("Sharing {} as {}:{} ({} chunks)", normalizedFile, peerIp, peerPort, count);
        return info;
    }

    public int getSharedFileCount() {
        return sharedFiles.size();
    }

    public List<SharingInfo> getSharedFiles() {
        return sharedFiles.values().stream().map(SharedFile::info).toList();
    }

    public synchronized void stopSharing(Path file) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        List<SharedFile> matches = sharedFiles.values().stream()
                .filter(shared -> shared.file.equals(normalizedFile))
                .toList();
        for (SharedFile shared : matches) {
            if (sharedFiles.remove(shared.info.peerPort(), shared)) {
                try {
                    shared.server.close();
                } catch (IOException e) {
                    log.warn("Could not close seeder port {}", shared.info.peerPort(), e);
                }
                sendTrackerMessage(shared, "UNREGISTER");
                log.info("Stopped sharing {} on port {}", normalizedFile, shared.info.peerPort());
            }
        }
    }

    @Scheduled(fixedRateString = "${p2p.tracker.heartbeat-ms:30000}")
    void sendHeartbeats() {
        sharedFiles.values().forEach(shared -> sendTrackerMessage(shared, "UPDATE"));
    }

    @PreDestroy
    void stop() {
        sharedFiles.values().forEach(shared -> {
            try {
                shared.server.close();
            } catch (IOException e) {
                log.debug("Error closing seeder socket", e);
            }
        });
        workers.shutdownNow();
    }

    private void acceptClients(SharedFile shared) {
        while (!shared.server.isClosed()) {
            try {
                Socket client = shared.server.accept();
                workers.submit(() -> serveChunkRequest(client, shared));
            } catch (IOException e) {
                if (!shared.server.isClosed()) {
                    log.warn("Seeder accept failed on port {}", shared.info.peerPort(), e);
                }
            }
        }
    }

    private void serveChunkRequest(Socket socket, SharedFile shared) {
        try (socket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
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
            try (RandomAccessFile file = new RandomAccessFile(shared.file.toFile(), "r")) {
                file.seek(offset);
                file.readFully(buffer);
            }
            output.write(buffer);
            output.flush();
        } catch (IOException e) {
            log.debug("Failed to serve chunk from {}:{}", shared.peerIp,
                    shared.info.peerPort(), e);
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
            int totalChunks) {
    }

    private record SharedFile(
            Path file,
            long fileSize,
            int totalChunks,
            ServerSocket server,
            String trackerHost,
            int trackerPort,
            String peerIp,
            String fileHash,
            SharingInfo info) {
    }
}