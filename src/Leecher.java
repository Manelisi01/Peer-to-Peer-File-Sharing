import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Leecher {

    private static final int TRACKER_PORT = 5000;
    private static final int CHUNK_SIZE = 512 * 1024;
    private static final int SOCKET_TIMEOUT = 10000;
    private static final int MAX_DOWNLOAD_THREADS = 8;

    private static JFrame frame;
    private static JLabel statusLabel;
    private static JProgressBar progressBar;
    private static JTextArea logArea;

    private static String targetFile;
    private static String expectedHash;
    private static String trackerIP;
    private static String leecherIP;
    private static int leecherPort;

    public static void main(String[] args) {

        if (args.length < 5) {
            System.err.println(
                "Usage: java Leecher <targetFile> <fileHash> " +
                "<trackerIP> <leecherIP> <leecherPort>"
            );
            return;
        }

        targetFile = args[0];
        expectedHash = args[1].toLowerCase();
        trackerIP = args[2];
        leecherIP = args[3];

        try {
            leecherPort = Integer.parseInt(args[4]);

            if (!expectedHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                    "The file hash must be a 64-character SHA-256 hash."
                );
            }

            if (leecherPort < 1 || leecherPort > 65535) {
                throw new IllegalArgumentException(
                    "The leecher port must be between 1 and 65535."
                );
            }

        } catch (NumberFormatException e) {
            System.err.println("Invalid leecher port: " + args[4]);
            return;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(Leecher::createGUI);

        new Thread(() -> {
            try {
                startDownload();
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                e.printStackTrace();
                updateStatus("Download failed.");
            }
        }, "Leecher-Main-Thread").start();
    }

    private static void createGUI() {
        frame = new JFrame("P2P File Sharing - Leecher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(650, 420);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        statusLabel = new JLabel("Connecting to tracker...");
        statusLabel.setBorder(
            BorderFactory.createEmptyBorder(10, 10, 0, 10)
        );

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(
            BorderFactory.createTitledBorder("Download Log")
        );

        JPanel topPanel = new JPanel(new BorderLayout(5, 10));
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static void startDownload() throws Exception {

        log("Target file: " + targetFile);
        log("Expected SHA-256: " + expectedHash);
        log("Querying tracker at " + trackerIP + ":" + TRACKER_PORT);

        List<InetSocketAddress> seeders =
            queryTracker(expectedHash, trackerIP);

        if (seeders.isEmpty()) {
            throw new IOException(
                "No seeders found for this file hash. " +
                "Make sure a Seeder is running and registered."
            );
        }

        log("Found " + seeders.size() + " seeder(s).");

        for (InetSocketAddress seeder : seeders) {
            log("Seeder: " + seeder.getAddress().getHostAddress()
                + ":" + seeder.getPort());
        }

        int totalChunks = getTotalChunks(seeders.get(0));

        if (totalChunks < 0) {
            throw new IOException("Seeder returned an invalid chunk count.");
        }

        log("Total chunks: " + totalChunks);

        Path destination = Paths.get(targetFile).toAbsolutePath();
        Path parent = destination.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryFile = Files.createTempFile(
            parent,
            "p2p-download-",
            ".part"
        );

        boolean completed = false;

        try {
            downloadChunks(seeders, totalChunks, temporaryFile);

            log("All chunks downloaded. Verifying SHA-256...");
            updateStatus("Verifying downloaded file...");

            String actualHash =
                FileHasher.calculateSHA256(temporaryFile.toString());

            log("Expected SHA-256: " + expectedHash);
            log("Actual SHA-256:   " + actualHash);

            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IOException(
                    "SHA-256 verification failed. The downloaded file " +
                    "does not match the expected file."
                );
            }

            log("SHA-256 verification successful.");

            try {
                Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

            completed = true;

            log("File saved to: " + destination);
            updateProgress(100);
            updateStatus("Download complete and verified.");

            log("Starting Seeder on port " + leecherPort + "...");

            startSeeder(destination.toString());

        } finally {
            if (!completed) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static List<InetSocketAddress> queryTracker(
            String fileHash,
            String trackerAddress) throws IOException {

        List<InetSocketAddress> seeders = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);

            String request = "QUERY|" + fileHash;

            byte[] requestData = request.getBytes();

            InetAddress tracker = InetAddress.getByName(trackerAddress);

            DatagramPacket requestPacket = new DatagramPacket(
                requestData,
                requestData.length,
                tracker,
                TRACKER_PORT
            );

            socket.send(requestPacket);

            byte[] responseBuffer = new byte[8192];

            DatagramPacket responsePacket = new DatagramPacket(
                responseBuffer,
                responseBuffer.length
            );

            socket.receive(responsePacket);

            String response = new String(
                responsePacket.getData(),
                0,
                responsePacket.getLength()
            ).trim();

            log("Tracker response: " + response);

            if (response.isEmpty()
                    || response.equalsIgnoreCase("NOT_FOUND")
                    || response.equalsIgnoreCase("NONE")) {
                return seeders;
            }

            String[] entries = response.split(",");

            for (String entry : entries) {
                entry = entry.trim();

                if (entry.isEmpty()) {
                    continue;
                }

                try {
                    seeders.add(parseSeederAddress(entry));
                } catch (IllegalArgumentException e) {
                    log("Ignoring invalid seeder address: " + entry);
                }
            }

        } catch (SocketTimeoutException e) {
            throw new IOException(
                "Tracker did not respond. Check that Tracker is running " +
                "on port " + TRACKER_PORT + ".",
                e
            );
        }

        return seeders;
    }

    private static InetSocketAddress parseSeederAddress(String address) {

        int separator = address.lastIndexOf(':');

        if (separator <= 0 || separator == address.length() - 1) {
            throw new IllegalArgumentException(
                "Expected IP:PORT, received: " + address
            );
        }

        String ip = address.substring(0, separator).trim();
        String portText = address.substring(separator + 1).trim();

        try {
            int port = Integer.parseInt(portText);

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                    "Invalid port in address: " + address
                );
            }

            return new InetSocketAddress(
                InetAddress.getByName(ip),
                port
            );

        } catch (NumberFormatException | UnknownHostException e) {
            throw new IllegalArgumentException(
                "Invalid seeder address: " + address,
                e
            );
        }
    }

    private static int getTotalChunks(
            InetSocketAddress seeder) throws IOException {

        try (Socket socket = new Socket()) {
            socket.connect(seeder, SOCKET_TIMEOUT);
            socket.setSoTimeout(SOCKET_TIMEOUT);

            DataOutputStream out = new DataOutputStream(
                socket.getOutputStream()
            );

            DataInputStream in = new DataInputStream(
                socket.getInputStream()
            );

            // The Seeder protocol uses -1 to request the chunk count.
            out.writeInt(-1);
            out.flush();

            int totalChunks = in.readInt();

            if (totalChunks < 0) {
                throw new IOException(
                    "Seeder returned a negative chunk count."
                );
            }

            return totalChunks;
        }
    }

    private static void downloadChunks(
            List<InetSocketAddress> seeders,
            int totalChunks,
            Path temporaryFile) throws Exception {

        if (totalChunks == 0) {
            log("The file contains zero chunks.");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(MAX_DOWNLOAD_THREADS, totalChunks)
        );

        List<Future<ChunkResult>> futures = new ArrayList<>();

        try (RandomAccessFile output = new RandomAccessFile(
                temporaryFile.toFile(), "rw")) {

            output.setLength((long) totalChunks * CHUNK_SIZE);

            for (int chunkNumber = 0;
                    chunkNumber < totalChunks;
                    chunkNumber++) {

                final int currentChunk = chunkNumber;

                Future<ChunkResult> future = executor.submit(() -> {
                    Exception lastError = null;

                    for (int attempt = 0;
                            attempt < seeders.size();
                            attempt++) {

                        InetSocketAddress seeder =
                            seeders.get((currentChunk + attempt)
                                % seeders.size());

                        try {
                            byte[] data = downloadChunk(
                                seeder,
                                currentChunk
                            );

                            return new ChunkResult(currentChunk, data);

                        } catch (Exception e) {
                            lastError = e;
                            log("Chunk " + currentChunk
                                + " failed from "
                                + seeder.getAddress().getHostAddress()
                                + ":" + seeder.getPort()
                                + ". Trying another seeder if available.");
                        }
                    }

                    throw new IOException(
                        "Could not download chunk " + currentChunk
                        + " from any available seeder.",
                        lastError
                    );
                });

                futures.add(future);
            }

            int completedChunks = 0;

            for (Future<ChunkResult> future : futures) {
                ChunkResult result = future.get();

                long position = (long) result.chunkNumber * CHUNK_SIZE;

                output.seek(position);
                output.write(result.data);

                completedChunks++;

                int percentage =
                    (int) ((completedChunks * 100.0) / totalChunks);

                updateProgress(percentage);

                log("Downloaded chunk " + result.chunkNumber
                    + " (" + completedChunks + "/" + totalChunks + ")");
            }

        } finally {
            executor.shutdownNow();
        }
    }

    private static byte[] downloadChunk(
            InetSocketAddress seeder,
            int chunkNumber) throws IOException {

        try (Socket socket = new Socket()) {
            socket.connect(seeder, SOCKET_TIMEOUT);
            socket.setSoTimeout(SOCKET_TIMEOUT);

            DataOutputStream out = new DataOutputStream(
                socket.getOutputStream()
            );

            InputStream in = socket.getInputStream();

            // Send the requested chunk number.
            out.writeInt(chunkNumber);
            out.flush();

            ByteArrayOutputStream buffer =
                new ByteArrayOutputStream(CHUNK_SIZE);

            byte[] data = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);

                if (buffer.size() > CHUNK_SIZE) {
                    throw new IOException(
                        "Seeder sent more data than the maximum chunk size."
                    );
                }
            }

            byte[] chunkData = buffer.toByteArray();

            if (chunkData.length == 0) {
                throw new IOException(
                    "Seeder returned an empty chunk."
                );
            }

            return chunkData;
        }
    }

    private static void startSeeder(String filePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-cp",
                "bin",
                "Seeder",
                filePath,
                trackerIP,
                leecherIP,
                String.valueOf(leecherPort)
            );

            processBuilder.redirectErrorStream(true);
            processBuilder.inheritIO();

            processBuilder.start();

            log("Seeder process started.");

        } catch (IOException e) {
            log("Could not start Seeder automatically: " + e.getMessage());
            log("You can start it manually using the command shown below:");
            log("java -cp bin Seeder \"" + filePath + "\" "
                + trackerIP + " " + leecherIP + " " + leecherPort);
        }
    }

    private static void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    private static void updateProgress(int percentage) {
        SwingUtilities.invokeLater(() -> {
            if (progressBar != null) {
                progressBar.setValue(percentage);
            }
        });
    }

    private static void log(String message) {
        System.out.println(message);

        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(message + "\n");
                logArea.setCaretPosition(
                    logArea.getDocument().getLength()
                );
            }
        });
    }

    private static class ChunkResult {
        private final int chunkNumber;
        private final byte[] data;

        private ChunkResult(int chunkNumber, byte[] data) {
            this.chunkNumber = chunkNumber;
            this.data = data;
        }
    }
}
