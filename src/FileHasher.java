import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class FileHasher {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Calculates the SHA-256 hash of a file.
     *
     * @param filePath path to the file
     * @return SHA-256 hash as a lowercase hexadecimal string
     */
    public static String calculateSHA256(String filePath) throws Exception {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];

            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return bytesToHex(digest.digest());
    }

    /**
     * Converts bytes to lowercase hexadecimal.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }

    /**
     * Allows the hash utility to be used directly from the command line.
     */
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: java FileHasher <filePath>");
            return;
        }

        String filePath = args[0];

        String hash = calculateSHA256(filePath);

        System.out.println("File: " + filePath);
        System.out.println("SHA-256: " + hash);
    }
}