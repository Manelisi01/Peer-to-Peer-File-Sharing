import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Tracker {

    private static final int PORT = 5000;
    private static final int BUFFER_SIZE = 8192;

    private static final long PEER_TIMEOUT = 60_000;
    private static final long CLEANUP_INTERVAL = 5;

    private static final Pattern SHA256_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final ConcurrentHashMap<String, PeerInfo> peers =
            new ConcurrentHashMap<>();

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {

        scheduler.scheduleAtFixedRate(
                Tracker::cleanupPeers,
                CLEANUP_INTERVAL,
                CLEANUP_INTERVAL,
                TimeUnit.SECONDS
        );

        try (DatagramSocket socket = new DatagramSocket(PORT)) {

            System.out.println("Tracker started on UDP port " + PORT);
            System.out.println("Waiting for Seeder and Leecher messages...");

            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                ).trim();

                System.out.println("Received: " + message);

                try {
                    handleMessage(socket, packet, message);
                } catch (IllegalArgumentException e) {
                    System.err.println(
                            "Rejected malformed message: "
                                    + e.getMessage()
                    );
                } catch (Exception e) {
                    System.err.println(
                            "Error processing message: "
                                    + e.getMessage()
                    );
                }
            }

        } catch (SocketException e) {
            System.err.println(
                    "Could not start Tracker on port " + PORT
                            + ". Is another process using this port?"
            );
        } catch (Exception e) {
            System.err.println("Tracker error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static void handleMessage(
            DatagramSocket socket,
            DatagramPacket packet,
            String message) throws Exception {

        String[] parts = message.split("\\|", -1);

        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Empty command.");
        }

        switch (parts[0]) {

            case "REGISTER":
                handleRegister(parts, packet);
                break;

            case "UPDATE":
                handleUpdate(parts);
                break;

            case "QUERY":
                handleQuery(socket, packet, parts);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unknown command: " + parts[0]
                );
        }
    }

    private static void handleRegister(
            String[] parts,
            DatagramPacket packet) {

        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "REGISTER requires: REGISTER|hash|ip|port"
            );
        }

        String fileHash = validateHash(parts[1]);
        String ip = validateIP(parts[2]);
        int port = validatePort(parts[3]);

        String peerKey = createPeerKey(ip, port);

        peers.put(
                peerKey,
                new PeerInfo(ip, port, fileHash)
        );

        System.out.println(
                "Registered peer " + ip + ":" + port
                        + " for SHA-256 " + fileHash
        );
    }

    private static void handleUpdate(String[] parts) {

        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "UPDATE requires: UPDATE|hash|ip|port"
            );
        }

        String fileHash = validateHash(parts[1]);
        String ip = validateIP(parts[2]);
        int port = validatePort(parts[3]);

        String peerKey = createPeerKey(ip, port);

        PeerInfo peer = peers.get(peerKey);

        if (peer == null) {
            System.out.println(
                    "Ignored heartbeat from unregistered peer "
                            + ip + ":" + port
            );
            return;
        }

        if (!peer.fileHash.equals(fileHash)) {
            System.out.println(
                    "Ignored heartbeat with mismatched file hash from "
                            + ip + ":" + port
            );
            return;
        }

        peer.lastSeen = System.currentTimeMillis();

        System.out.println(
                "Heartbeat updated for " + ip + ":" + port
        );
    }

    private static void handleQuery(
            DatagramSocket socket,
            DatagramPacket packet,
            String[] parts) throws Exception {

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "QUERY requires: QUERY|hash"
            );
        }

        String fileHash = validateHash(parts[1]);

        List<String> matchingPeers = new ArrayList<>();

        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            PeerInfo peer = entry.getValue();

            if (peer.fileHash.equals(fileHash)
                    && !isStale(peer)) {
                matchingPeers.add(peer.toString());
            }
        }

        String response = String.join(",", matchingPeers);

        byte[] responseData =
                response.getBytes(StandardCharsets.UTF_8);

        DatagramPacket responsePacket = new DatagramPacket(
                responseData,
                responseData.length,
                packet.getAddress(),
                packet.getPort()
        );

        socket.send(responsePacket);

        System.out.println(
                "Query for " + fileHash
                        + " returned " + matchingPeers.size()
                        + " peer(s): "
                        + (response.isEmpty() ? "(none)" : response)
        );
    }

    private static String validateHash(String hash) {

        if (hash == null || !SHA256_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException(
                    "File hash must be a 64-character SHA-256 hex string."
            );
        }

        return hash.toLowerCase();
    }

    private static String validateIP(String ip) {

        if (ip == null || ip.isBlank() || ip.contains(":")
                || ip.contains("|") || ip.contains(",")) {
            throw new IllegalArgumentException(
                    "Invalid IPv4 address: " + ip
            );
        }

        try {
            InetAddress address = InetAddress.getByName(ip);

            if (!(address instanceof java.net.Inet4Address)) {
                throw new IllegalArgumentException(
                        "Only IPv4 addresses are supported: " + ip
                );
            }

            return address.getHostAddress();

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid IPv4 address: " + ip,
                    e
            );
        }
    }

    private static int validatePort(String portText) {

        try {
            int port = Integer.parseInt(portText);

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "Port must be between 1 and 65535."
                );
            }

            return port;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid port: " + portText,
                    e
            );
        }
    }

    private static String createPeerKey(String ip, int port) {
        return ip + ":" + port;
    }

    private static boolean isStale(PeerInfo peer) {
        return System.currentTimeMillis() - peer.lastSeen > PEER_TIMEOUT;
    }

    private static void cleanupPeers() {

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, PeerInfo>> iterator =
                peers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, PeerInfo> entry = iterator.next();
            PeerInfo peer = entry.getValue();

            if (now - peer.lastSeen > PEER_TIMEOUT) {
                if (peers.remove(entry.getKey(), peer)) {
                    System.out.println(
                            "Removed stale peer: " + peer
                    );
                }
            }
        }
    }

    static class PeerInfo {

        final String ip;
        final int port;
        final String fileHash;
        volatile long lastSeen;

        PeerInfo(String ip, int port, String fileHash) {
            this.ip = ip;
            this.port = port;
            this.fileHash = fileHash;
            this.lastSeen = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
