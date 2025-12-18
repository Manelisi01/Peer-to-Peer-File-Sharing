import javax.swing.*;
import java.awt.BorderLayout;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Leecher for downloading files from multiple seeders using P2P.
 * Supports large files and GUI progress display.
 */
public class Leecher {

    private static final int TRACKER_PORT = 5000;
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB

    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    public Leecher() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Download Progress");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        frame.add(progressBar, BorderLayout.CENTER);

        statusLabel = new JLabel("Waiting to start download...", SwingConstants.CENTER);
        frame.add(statusLabel, BorderLayout.NORTH);

        frame.setVisible(true);
    }

    private void updateProgress(int progress, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            statusLabel.setText(message);
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java Leecher <TARGET_FILE> <TRACKER_IP> <LEECHER_IP> <LEECHER_PORT>");
            return;
        }

        String TARGET_FILE = args[0];
        String TRACKER_IP = args[1];
        String LEECHER_IP = args[2];
        String LEECHER_PORT = args[3];

        Leecher leecher = new Leecher();
        leecher.startDownload(TARGET_FILE, TRACKER_IP, LEECHER_IP, LEECHER_PORT);
    }

    public void startDownload(String TARGET_FILE, String TRACKER_IP, String LEECHER_IP, String LEECHER_PORT) {
        new Thread(() -> {
            try {
                downloadFile(TARGET_FILE, TRACKER_IP, LEECHER_IP, LEECHER_PORT);
            } catch (Exception e) {
                e.printStackTrace();
                updateProgress(0, "Download failed: " + e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String TARGET_FILE, String TRACKER_IP, String LEECHER_IP, String LEECHER_PORT) throws Exception {
        updateProgress(0, "Querying tracker for seeders...");

        java.util.List<String> seeders = queryTracker(TARGET_FILE, TRACKER_IP);
        if (seeders.isEmpty()) {
            updateProgress(0, "No seeders available for this file!");
            return;
        }

        updateProgress(0, "Seeders found: " + seeders);
        String[] firstSeederParts = seeders.get(0).split(":");
        String seederIP = firstSeederParts[0];
        int seederPort = Integer.parseInt(firstSeederParts[1]);

        int totalChunks = getTotalChunks(seederIP, seederPort);
        if (totalChunks <= 0) {
            updateProgress(0, "Failed to retrieve total chunks.");
            return;
        }

        updateProgress(0, "Total chunks: " + totalChunks);

        // Prepare output file
        RandomAccessFile raf = new RandomAccessFile(TARGET_FILE, "rw");
        raf.setLength((long) totalChunks * CHUNK_SIZE);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(seeders.size(), 8));
        CountDownLatch latch = new CountDownLatch(totalChunks);
        AtomicInteger chunksDownloaded = new AtomicInteger(0);

        for (int i = 0; i < totalChunks; i++) {
            final int chunkNumber = i;

            executor.submit(() -> {
                String seeder = seeders.get(chunkNumber % seeders.size());
                String[] seederParts = seeder.split(":");
                String ip = seederParts[0];
                int port = Integer.parseInt(seederParts[1]);

                try {
                    byte[] chunkData = downloadChunk(ip, port, chunkNumber);
                    if (chunkData != null && chunkData.length > 0) {
                        synchronized (raf) {
                            raf.seek((long) chunkNumber * CHUNK_SIZE);
                            raf.write(chunkData);
                        }
                    } else {
                        System.err.println("Chunk " + chunkNumber + " failed from " + seeder);
                    }
                } catch (Exception e) {
                    System.err.println("Error downloading chunk " + chunkNumber + " from " + seeder);
                    e.printStackTrace();
                } finally {
                    int completed = chunksDownloaded.incrementAndGet();
                    updateProgress((int) ((double) completed / totalChunks * 100),
                            "Downloading chunk " + chunkNumber + " (" + completed + "/" + totalChunks + ")");
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await();
        raf.close();

        updateProgress(100, "Download completed: " + TARGET_FILE);

        // Become a seeder for the downloaded file
        Seeder.main(new String[]{TARGET_FILE, TRACKER_IP, LEECHER_IP, LEECHER_PORT});
    }

    private java.util.List<String> queryTracker(String fileHash, String TRACKER_IP) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "QUERY|" + fileHash;
            byte[] data = message.getBytes();
            InetAddress address = InetAddress.getByName(TRACKER_IP);
            socket.send(new DatagramPacket(data, data.length, address, TRACKER_PORT));

            byte[] buffer = new byte[1024000];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            String responseData = new String(response.getData(), 0, response.getLength()).trim();
            if (responseData.isEmpty()) return Collections.emptyList();
            return Arrays.asList(responseData.split(","));
        }
    }

    private byte[] downloadChunk(String ip, int port, int chunkNumber) {
        try (Socket socket = new Socket(ip, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeInt(chunkNumber);
            int length = in.readInt();
            byte[] chunk = new byte[length];
            in.readFully(chunk);
            return chunk;

        } catch (IOException e) {
            System.err.println("Failed chunk " + chunkNumber + " from " + ip + ":" + port);
            return new byte[0];
        }
    }

    private int getTotalChunks(String seederIP, int seederPort) {
        try (Socket socket = new Socket(seederIP, seederPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeInt(-1);
            return in.readInt();

        } catch (IOException e) {
            System.err.println("Failed to get total chunks from " + seederIP + ":" + seederPort);
            return 0;
        }
    }
}
