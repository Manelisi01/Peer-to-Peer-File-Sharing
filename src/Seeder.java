import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class Seeder {

    private static final int CHUNK_SIZE = 512 * 1024;
    private static final int TRACKER_PORT = 5000;

    public static void main(String[] args) throws Exception {

        if (args.length < 4) {
            System.err.println(
                    "Usage: java Seeder <filePath> <TRACKER_IP> <SEEDER_IP> <SEEDER_PORT>"
            );
            return;
        }

        String filePath = args[0];
        String trackerIP = args[1];
        String seederIP = args[2];
        int seederPort = Integer.parseInt(args[3]);

        Path file = Paths.get(filePath);

        if (!Files.exists(file)) {
            System.err.println("File does not exist: " + filePath);
            return;
        }

        if (!Files.isRegularFile(file)) {
            System.err.println("Path is not a regular file: " + filePath);
            return;
        }

        long fileSize = Files.size(file);

        long totalChunks = calculateTotalChunks(fileSize);

        // Calculate the real content-based file identity.
        String fileHash = FileHasher.calculateSHA256(filePath);

        System.out.println("========================================");
        System.out.println("           P2P Seeder Started");
        System.out.println("========================================");
        System.out.println("File: " + filePath);
        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Chunk size: " + CHUNK_SIZE + " bytes");
        System.out.println("Total chunks: " + totalChunks);
        System.out.println("SHA-256: " + fileHash);
        System.out.println("Seeder port: " + seederPort);
        System.out.println("Tracker: " + trackerIP + ":" + TRACKER_PORT);
        System.out.println("========================================");

        ExecutorService executor = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(seederPort)) {

            System.out.println("Seeder listening on port " + seederPort);

            register(fileHash, trackerIP, seederPort, seederIP);

            System.out.println(
                    "Registered with tracker for SHA-256: " + fileHash
            );

            ScheduledExecutorService scheduler =
                    Executors.newScheduledThreadPool(1);

            scheduler.scheduleAtFixedRate(
                    () -> {
                        update(
                                fileHash,
                                trackerIP,
                                seederPort,
                                seederIP
                        );

                        System.out.println("Sent heartbeat to tracker.");
                    },
                    0,
                    30,
                    TimeUnit.SECONDS
            );

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println(
                        "New client connected: "
                                + clientSocket.getInetAddress()
                );

                executor.submit(
                        () -> handleClient(
                                clientSocket,
                                file,
                                fileSize,
                                totalChunks
                        )
                );
            }
        }
    }

    private static long calculateTotalChunks(long fileSize) {

        if (fileSize == 0) {
            return 0;
        }

        return (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
    }

    private static void register(
            String fileHash,
            String trackerIP,
            int port,
            String seederIP) {

        try (DatagramSocket socket = new DatagramSocket()) {

            String message =
                    "REGISTER|"
                            + fileHash
                            + "|"
                            + seederIP
                            + "|"
                            + port;

            byte[] data = message.getBytes();

            InetAddress address =
                    InetAddress.getByName(trackerIP);

            socket.send(
                    new DatagramPacket(
                            data,
                            data.length,
                            address,
                            TRACKER_PORT
                    )
            );

        } catch (Exception e) {

            System.err.println(
                    "Failed to register with tracker: "
                            + e.getMessage()
            );
        }
    }

    private static void update(
            String fileHash,
            String trackerIP,
            int port,
            String seederIP) {

        try (DatagramSocket socket = new DatagramSocket()) {

            String message =
                    "UPDATE|"
                            + fileHash
                            + "|"
                            + seederIP
                            + "|"
                            + port;

            byte[] data = message.getBytes();

            InetAddress address =
                    InetAddress.getByName(trackerIP);

            socket.send(
                    new DatagramPacket(
                            data,
                            data.length,
                            address,
                            TRACKER_PORT
                    )
            );

        } catch (Exception e) {

            System.err.println(
                    "Failed to send heartbeat to tracker: "
                            + e.getMessage()
            );
        }
    }

    private static void handleClient(
            Socket socket,
            Path file,
            long fileSize,
            long totalChunks) {

        try (
                DataInputStream in =
                        new DataInputStream(socket.getInputStream());

                DataOutputStream out =
                        new DataOutputStream(socket.getOutputStream())
        ) {

            int chunkNumber = in.readInt();

            if (chunkNumber == -1) {

                out.writeInt((int) totalChunks);
                out.flush();

                System.out.println(
                        "Sent total chunks to client: "
                                + totalChunks
                );

                return;
            }

            if (chunkNumber < 0 || chunkNumber >= totalChunks) {

                System.err.println(
                        "Invalid chunk requested: "
                                + chunkNumber
                );

                return;
            }

            long offset =
                    (long) chunkNumber * CHUNK_SIZE;

            long remaining =
                    fileSize - offset;

            int bytesToSend =
                    (int) Math.min(
                            CHUNK_SIZE,
                            remaining
                    );

            byte[] buffer =
                    new byte[CHUNK_SIZE];

            try (RandomAccessFile randomAccessFile =
                         new RandomAccessFile(file.toFile(), "r")) {

                randomAccessFile.seek(offset);

                int bytesRead =
                        randomAccessFile.read(
                                buffer,
                                0,
                                bytesToSend
                        );

                if (bytesRead > 0) {

                    out.write(
                            buffer,
                            0,
                            bytesRead
                    );

                    out.flush();

                    System.out.println(
                            "Sent chunk "
                                    + chunkNumber
                                    + " ("
                                    + bytesRead
                                    + " bytes)"
                    );
                }
            }

        } catch (IOException e) {

            System.err.println(
                    "Error handling client request: "
                            + e.getMessage()
            );

        } finally {

            try {
                socket.close();
            } catch (IOException e) {
                System.err.println(
                        "Error closing client socket: "
                                + e.getMessage()
                );
            }
        }
    }
}