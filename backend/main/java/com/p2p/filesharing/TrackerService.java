package com.p2p.filesharing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrackerService {

    private static final Logger log = LoggerFactory.getLogger(TrackerService.class);
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int BUFFER_SIZE = 8192;

    @Value("${p2p.tracker.udp-port:5000}")
    private int udpPort;

    @Value("${p2p.tracker.enabled:true}")
    private boolean enabled;

    @Value("${p2p.tracker.peer-timeout-ms:60000}")
    private long peerTimeoutMs;

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile DatagramSocket serverSocket;
    private volatile Thread serverThread;

    @PostConstruct
    void start() throws IOException {
        if (!enabled) {
            log.info("Embedded P2P tracker is disabled.");
            return;
        }
        serverSocket = new DatagramSocket(udpPort);
        serverThread = new Thread(this::listen, "p2p-tracker-udp");
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("P2P tracker listening on UDP port {}", udpPort);
    }

    @PreDestroy
    void stop() {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    public List<PeerAddress> getPeers(String fileHash) {
        String hash = validateHash(fileHash);
        long now = System.currentTimeMillis();
        List<PeerAddress> result = new ArrayList<>();
        for (PeerInfo peer : peers.values()) {
            if (peer.fileHash.equals(hash) && now - peer.lastSeen <= peerTimeoutMs) {
                result.add(new PeerAddress(peer.ip, peer.port));
            }
        }
        return result;
    }

    public List<InetSocketAddress> query(String fileHash, String trackerHost, int trackerPort)
            throws IOException {
        String request = "QUERY|" + validateHash(fileHash);
        byte[] requestBytes = request.getBytes(StandardCharsets.UTF_8);
        InetAddress trackerAddress = InetAddress.getByName(trackerHost);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            socket.send(new DatagramPacket(
                    requestBytes, requestBytes.length, trackerAddress, trackerPort));

            byte[] responseBytes = new byte[BUFFER_SIZE];
            DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
            socket.receive(response);
            String payload = new String(response.getData(), response.getOffset(),
                    response.getLength(), StandardCharsets.UTF_8).trim();

            List<InetSocketAddress> result = new ArrayList<>();
            if (payload.isEmpty() || payload.equalsIgnoreCase("NOT_FOUND")
                    || payload.equalsIgnoreCase("NONE")) {
                return result;
            }
            for (String entry : payload.split(",")) {
                result.add(parsePeerAddress(entry.trim()));
            }
            return result;
        } catch (SocketTimeoutException e) {
            throw new IOException("Tracker did not respond on UDP port " + trackerPort + ".", e);
        }
    }

    private void listen() {
        while (!serverSocket.isClosed()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                serverSocket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8).trim();
                handleMessage(packet, message);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log.warn("Tracker UDP receive failed", e);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Rejected malformed tracker message: {}", e.getMessage());
            }
        }
    }

    private void handleMessage(DatagramPacket packet, String message) throws IOException {
        String[] parts = message.split("\\|", -1);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty command.");
        }

        switch (parts[0]) {
            case "REGISTER", "UPDATE" -> updatePeer(parts);
            case "UNREGISTER" -> removePeer(parts);
            case "QUERY" -> {
                if (parts.length != 2) {
                    throw new IllegalArgumentException("QUERY requires: QUERY|hash");
                }
                List<String> matches = getPeers(parts[1]).stream()
                        .map(peer -> peer.ip() + ":" + peer.port())
                        .toList();
                byte[] response = String.join(",", matches).getBytes(StandardCharsets.UTF_8);
                serverSocket.send(new DatagramPacket(response, response.length,
                        packet.getAddress(), packet.getPort()));
            }
            default -> throw new IllegalArgumentException("Unknown command: " + parts[0]);
        }
    }

    private void updatePeer(String[] parts) {
        if (parts.length != 4) {
            throw new IllegalArgumentException(parts[0] + " requires: command|hash|ip|port");
        }
        String hash = validateHash(parts[1]);
        String ip = validateIp(parts[2]);
        int port = validatePort(parts[3]);
        String key = ip + ":" + port;
        PeerInfo current = peers.get(key);
        if ("UPDATE".equals(parts[0])) {
            if (current == null || !current.fileHash.equals(hash)) {
                return;
            }
        }
        peers.put(key, new PeerInfo(ip, port, hash, System.currentTimeMillis()));
    }

    private void removePeer(String[] parts) {
        if (parts.length != 4) {
            throw new IllegalArgumentException("UNREGISTER requires: UNREGISTER|hash|ip|port");
        }
        String hash = validateHash(parts[1]);
        String ip = validateIp(parts[2]);
        int port = validatePort(parts[3]);
        String key = ip + ":" + port;
        peers.computeIfPresent(key, (peerKey, peer) -> peer.fileHash.equals(hash) ? null : peer);
    }

    @Scheduled(fixedDelayString = "${p2p.tracker.cleanup-interval-ms:5000}")
    void removeStalePeers() {
        long cutoff = System.currentTimeMillis() - peerTimeoutMs;
        peers.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoff);
    }

    private static InetSocketAddress parsePeerAddress(String address) {
        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            throw new IllegalArgumentException("Invalid peer address: " + address);
        }
        String ip = validateIp(address.substring(0, separator));
        int port = validatePort(address.substring(separator + 1));
        try {
            return new InetSocketAddress(InetAddress.getByName(ip), port);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid peer address: " + address, e);
        }
    }

    private static String validateHash(String hash) {
        if (hash == null || !HASH_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException("File hash must be a 64-character SHA-256 hex string.");
        }
        return hash.toLowerCase();
    }

    private static String validateIp(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!(address instanceof Inet4Address)) {
                throw new IllegalArgumentException("Only IPv4 peer addresses are supported.");
            }
            return address.getHostAddress();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip, e);
        }
    }

    private static int validatePort(String portText) {
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + portText, e);
        }
    }

    private record PeerInfo(String ip, int port, String fileHash, long lastSeen) {
    }
}