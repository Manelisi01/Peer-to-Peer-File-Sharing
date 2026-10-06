import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * The Seeder class is responsible for sharing a file with leechers
 * in a peer-to-peer (P2P) network.
 *
 * The Seeder:
 * 1. Registers itself with the Tracker using UDP.
 * 2. Sends periodic heartbeats to the Tracker.
 * 3. Listens for Leecher connections using TCP.
 * 4. Serves individual file chunks on request.
 *
 * Unlike the original implementation, this version does NOT load the
 * entire file into memory. Chunks are read directly from disk only
 * when they are requested.
 */
public class Seeder {

    // Size of each file chunk: 512 KB
    private static final int CHUNK_SIZE = 512 * 1024;

    // Port used by the Tracker
    private static final int TRACKER_PORT = 5000;

    /**
     * Main method to start the Seeder.
     *
     * Arguments:
     * <filePath> <TRACKER_IP> <SEEDER_IP> <SEEDER_PORT>
     *
     * @param args command-line arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {

        // Validate command-line arguments
        if (args.length < 4) {
            System.err.println(
                    "Usage: java Seeder <filePath> <TRACKER_IP> <SEEDER_IP> <SEEDER_PORT>"
            );
            return;
        }

        // Parse command-line arguments
        String filePath = args[0];
        String trackerIP = args[1];
        String seederIP = args[2];
        int seederPort = Integer.parseInt(args[3]);

        // Validate that the file exists
        Path file = Paths.get(filePath);

        if (!Files.exists(file)) {
            System.err.println("File does not exist: " + filePath);
            return;
        }

        if (!Files.isRegularFile(file)) {
            System.err.println("Path is not a regular file: " + filePath);
            return;
        }

        // Get file size
        long fileSize = Files.size(file);

        // Calculate number of chunks
        long totalChunks = calculateTotalChunks(fileSize);

        System.out.println("========================================");
        System.out.println("           P2P Seeder Started");
        System.out.println("========================================");
        System.out.println("File: " + filePath);
        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Chunk size: " + CHUNK_SIZE + " bytes");
        System.out.println("Total chunks: " + totalChunks);
        System.out.println("Seeder port: " + seederPort);
        System.out.println("Tracker: " + trackerIP + ":" + TRACKER_PORT);
        System.out.println("========================================");

        // Thread pool for handling multiple Leecher connections
        ExecutorService executor = Executors.newCachedThreadPool();

        // Open TCP server
        try (ServerSocket serverSocket = new ServerSocket(seederPort)) {

            System.out.println("Seeder listening on port " + seederPort);

            /*
             * Register this Seeder with the Tracker.
             *
             * We keep the existing protocol:
             *
             * REGISTER|filePath|IP|port
             *
             * This means the existing Tracker does not need to change yet.
             */
            register(
                    filePath,
                    trackerIP,
                    seederPort,
                    seederIP
            );

            System.out.println("Registered with tracker for file: " + filePath);

            /*
             * Start heartbeat scheduler.
             *
             * The Seeder sends an UPDATE message every 30 seconds
             * so that the Tracker knows it is still alive.
             */
            ScheduledExecutorService scheduler =
                    Executors.newScheduledThreadPool(1);

            scheduler.scheduleAtFixedRate(
                    () -> {
                        update(
                                filePath,
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

            /*
             * Main TCP server loop.
             *
             * Every Leecher connection gets its own task.
             */
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

    /**
     * Calculates how many chunks are required to represent the file.
     *
     * Example:
     *
     * File size = 1,000,000 bytes
     * Chunk size = 524,288 bytes
     *
     * Total chunks = 2
     *
     * @param fileSize size of the file in bytes
     * @return number of chunks
     */
    private static long calculateTotalChunks(long fileSize) {

        if (fileSize == 0) {
            return 0;
        }

        return (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
    }

    /**
     * Registers this Seeder with the Tracker.
     *
     * Existing protocol:
     *
     * REGISTER|filePath|SEEDER_IP|port
     *
     * @param fileHash file identifier
     * @param trackerIP Tracker IP address
     * @param port Seeder TCP port
     * @param seederIP Seeder IP address
     */
    private static void register(
            String fileHash,
            String trackerIP,
            int port,
            String seederIP
    ) {

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

            DatagramPacket packet =
                    new DatagramPacket(
                            data,
                            data.length,
                            address,
                            TRACKER_PORT
                    );

            socket.send(packet);

        } catch (Exception e) {

            System.err.println(
                    "Failed to register with tracker: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Sends a heartbeat/update to the Tracker.
     *
     * Existing protocol:
     *
     * UPDATE|filePath|SEEDER_IP|port
     *
     * @param fileHash file identifier
     * @param trackerIP Tracker IP address
     * @param port Seeder TCP port
     * @param seederIP Seeder IP address
     */
    private static void update(
            String fileHash,
            String trackerIP,
            int port,
            String seederIP
    ) {

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

            DatagramPacket packet =
                    new DatagramPacket(
                            data,
                            data.length,
                            address,
                            TRACKER_PORT
                    );

            socket.send(packet);

        } catch (Exception e) {

            System.err.println(
                    "Failed to send heartbeat to tracker: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Handles a TCP connection from a Leecher.
     *
     * The existing protocol is preserved:
     *
     * chunkNumber == -1
     *      -> return total number of chunks
     *
     * chunkNumber >= 0
     *      -> return requested chunk
     *
     * @param socket client socket
     * @param file file being shared
     * @param fileSize size of the file
     * @param totalChunks number of chunks
     */
    private static void handleClient(
            Socket socket,
            Path file,
            long fileSize,
            long totalChunks
    ) {

        try (
                DataInputStream in =
                        new DataInputStream(socket.getInputStream());

                DataOutputStream out =
                        new DataOutputStream(socket.getOutputStream())
        ) {

            // Read requested chunk number
            int chunkNumber = in.readInt();

            /*
             * Special request:
             *
             * -1 means the Leecher wants to know
             * how many chunks the file contains.
             */
            if (chunkNumber == -1) {

                out.writeInt((int) totalChunks);
                out.flush();

                System.out.println(
                        "Sent total chunks to client: "
                                + totalChunks
                );

                return;
            }

            /*
             * Validate requested chunk.
             */
            if (chunkNumber < 0 || chunkNumber >= totalChunks) {

                System.err.println(
                        "Invalid chunk requested: "
                                + chunkNumber
                );

                return;
            }

            /*
             * Calculate where this chunk begins in the file.
             *
             * Example:
             *
             * chunk 0 -> byte 0
             * chunk 1 -> byte 524288
             * chunk 2 -> byte 1048576
             */
            long offset =
                    (long) chunkNumber * CHUNK_SIZE;

            /*
             * Determine how many bytes this particular
             * chunk contains.
             *
             * The final chunk may be smaller than CHUNK_SIZE.
             */
            long remainingBytes =
                    fileSize - offset;

            int bytesToSend =
                    (int) Math.min(
                            CHUNK_SIZE,
                            remainingBytes
                    );

            System.out.println(
                    "Sending chunk "
                            + chunkNumber
                            + " ("
                            + bytesToSend
                            + " bytes)"
            );

            /*
             * RandomAccessFile allows us to jump directly
             * to the requested chunk without loading the
             * entire file into memory.
             */
            try (RandomAccessFile randomAccessFile =
                         new RandomAccessFile(file.toFile(), "r")) {

                randomAccessFile.seek(offset);

                byte[] buffer =
                        new byte[CHUNK_SIZE];

                int totalBytesRead = 0;

                while (totalBytesRead < bytesToSend) {

                    int bytesRead =
                            randomAccessFile.read(
                                    buffer,
                                    totalBytesRead,
                                    bytesToSend - totalBytesRead
                            );

                    if (bytesRead == -1) {
                        break;
                    }

                    totalBytesRead += bytesRead;
                }

                /*
                 * Send exactly the number of bytes belonging
                 * to this chunk.
                 */
                out.write(
                        buffer,
                        0,
                        totalBytesRead
                );

                out.flush();

                System.out.println(
                        "Successfully sent chunk "
                                + chunkNumber
                                + " ("
                                + totalBytesRead
                                + " bytes)"
                );
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