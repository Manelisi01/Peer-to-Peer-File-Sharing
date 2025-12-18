import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * The Seeder class is responsible for sharing a file with leechers in a peer-to-peer (P2P) network.
 * It splits the file into chunks, registers with a tracker, and serves chunks to clients upon request.
 * It also sends periodic heartbeats to the tracker to maintain its availability status.
 */
public class Seeder {

    // Constants
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB chunk size
    private static final int TRACKER_PORT = 5000; // Port for the tracker

    /**
     * The main method to start the Seeder application.
     *
     * @param args Command-line arguments: <filePath> <TRACKER_IP> <SEEDER_IP> <SEEDER_PORT>.
     * @throws Exception If an error occurs during execution.
     */
    public static void main(String[] args) throws Exception {

        // Validate command-line arguments
        if (args.length < 4) {
            System.err.println("Usage: java Seeder <filePath> <TRACKER_IP> <SEEDER_IP> <SEEDER_PORT>");
            return;
        }

        // Parse command-line arguments
        String filePath = args[0]; // Path to the file to be shared
        String TRACKER_IP = args[1]; // IP address of the tracker
        String SEEDER_IP = args[2]; // IP address of this seeder
        String SEEDER_PORT = args[3]; // Port on which this seeder will listen

        int seederPort = Integer.parseInt(SEEDER_PORT); // Convert port to integer

        // Split the file into chunks
        int chunks = getTotalChunks(filePath);
        System.out.println("File split into " + chunks + " chunks.");

        // Start TCP server to handle client requests
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(seederPort)) {
            System.out.println("Seeder started on port " + seederPort);

            // Register with the tracker
            register(filePath, TRACKER_IP, seederPort, SEEDER_IP);
            System.out.println("Registered with tracker for file: " + filePath);

            // Schedule periodic heartbeats to the tracker
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(new Runnable() {

                public void run() {

                    update(filePath, TRACKER_IP, seederPort, SEEDER_IP);
                    System.out.println("Sent heartbeat to tracker.");
                }
            }, 0, 30, TimeUnit.SECONDS);

            // Handle incoming TCP connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Submit a new task to handle the client
                executor.submit(() -> handleClient(clientSocket, filePath));

            }
        }
    }

    /**
     * Registers the seeder with the tracker.
     *
     * @param fileHash   The hash of the file being shared.
     * @param TRACKER_IP The IP address of the tracker.
     * @param port       The port on which the seeder is listening.
     * @param SEEDER_IP  The IP address of the seeder.
     */
    private static void register(String fileHash, String TRACKER_IP, int port, String SEEDER_IP) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "REGISTER|" + fileHash + "|" + SEEDER_IP + "|" + port;
            byte[] data = message.getBytes();
            InetAddress address = InetAddress.getByName(TRACKER_IP);
            socket.send(new DatagramPacket(data, data.length, address, TRACKER_PORT));
        } catch (Exception e) {
            System.err.println("Failed to register with tracker: " + e.getMessage());
        }
    }

    /**
     * Sends a periodic update (heartbeat) to the tracker.
     *
     * @param fileHash   The hash of the file being shared.
     * @param TRACKER_IP The IP address of the tracker.
     * @param port       The port on which the seeder is listening.
     * @param SEEDER_IP  The IP address of the seeder.
     */
    private static void update(String fileHash, String TRACKER_IP, int port, String SEEDER_IP) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "UPDATE|" + fileHash + "|" + SEEDER_IP + "|" + port;
            byte[] data = message.getBytes();
            InetAddress address = InetAddress.getByName(TRACKER_IP);
            socket.send(new DatagramPacket(data, data.length, address, TRACKER_PORT));
        } catch (Exception e) {
            System.err.println("Failed to send heartbeat to tracker: " + e.getMessage());
        }
    }

    /**
     * Splits the file into chunks of size CHUNK_SIZE.
     *
     * @param filePath The path to the file.
     * @return A 2D byte array containing the file chunks.
     * @throws IOException If an I/O error occurs.
     */
  private static int getTotalChunks(String filePath) throws IOException {
    long fileSize = Files.size(Paths.get(filePath));
    return (int) Math.ceil((double) fileSize / CHUNK_SIZE);
}

private static byte[] readChunk(String filePath, int chunkNumber) throws IOException {
    try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
        long position = (long) chunkNumber * CHUNK_SIZE;
        file.seek(position);

        int remaining = (int) Math.min(CHUNK_SIZE, file.length() - position);
        byte[] buffer = new byte[remaining];
        file.readFully(buffer);

        return buffer;
    }
}


    /**
     * Handles a client connection by serving the requested chunk.
     *
     * @param socket The client socket.
     * @param chunks The array of file chunks.
     * 
     */


   private static void handleClient(Socket socket, String filePath) {
    try (DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

        int chunkNumber = in.readInt();

        if (chunkNumber == -1) {
            int totalChunks = getTotalChunks(filePath);
            out.writeInt(totalChunks);
            System.out.println("Sent total chunks: " + totalChunks);
        } else {
            byte[] chunk = readChunk(filePath, chunkNumber);
            out.writeInt(chunk.length);   // IMPORTANT
            out.write(chunk);
            System.out.println("Sent chunk " + chunkNumber);
        }
    } catch (IOException e) {
        System.err.println("Client error: " + e.getMessage());
    }
}


}
