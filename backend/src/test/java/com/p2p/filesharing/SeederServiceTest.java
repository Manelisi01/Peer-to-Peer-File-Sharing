package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class SeederServiceTest {

    private static final int CHUNK = 1000;

    @TempDir
    Path dir;

    @Test
    void sharesSeveralFilesOnOnePortAndServesEachByHash() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        byte[] dataA = randomBytes(2500, 1);
        byte[] dataB = randomBytes(1200, 2);
        Path a = write("a.bin", dataA);
        Path b = write("b.bin", dataB);

        SeederService.SharingInfo infoA = seeder.startSharing(a, "127.0.0.1", port, "127.0.0.1", 9);
        SeederService.SharingInfo infoB = seeder.startSharing(b, "127.0.0.1", port, "127.0.0.1", 9);

        assertEquals(2, seeder.getSharedFileCount());
        assertEquals(3, requestChunkCount(port, infoA.fileHash()));
        assertEquals(2, requestChunkCount(port, infoB.fileHash()));
        assertArrayEquals(slice(dataA, 2), requestChunk(port, infoA.fileHash(), 2));
        assertArrayEquals(slice(dataB, 0), requestChunk(port, infoB.fileHash(), 0));

        seeder.stop();
    }

    @Test
    void unknownHashIsNotServed() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        seeder.startSharing(write("a.bin", randomBytes(100, 3)), "127.0.0.1", port, "127.0.0.1", 9);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(new byte[32]);
            out.writeInt(-1);
            out.flush();
            assertEquals(-1, socket.getInputStream().read());
        }

        seeder.stop();
    }

    @Test
    void sameContentOnSamePortIsRejectedAndResharingIsIdempotent() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        byte[] data = randomBytes(300, 4);
        Path original = write("a.bin", data);
        Path copy = write("copy.bin", data);

        SeederService.SharingInfo first = seeder.startSharing(original, "127.0.0.1", port, "127.0.0.1", 9);
        assertEquals(first, seeder.startSharing(original, "127.0.0.1", port, "127.0.0.1", 9));
        assertThrows(IllegalArgumentException.class,
                () -> seeder.startSharing(copy, "127.0.0.1", port, "127.0.0.1", 9));
        assertEquals(1, seeder.getSharedFileCount());

        seeder.stop();
    }

    @Test
    void stoppingOneFileKeepsOthersAndClosesPortWithTheLastOne() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        byte[] dataB = randomBytes(1500, 6);
        Path a = write("a.bin", randomBytes(1500, 5));
        Path b = write("b.bin", dataB);
        seeder.startSharing(a, "127.0.0.1", port, "127.0.0.1", 9);
        SeederService.SharingInfo infoB = seeder.startSharing(b, "127.0.0.1", port, "127.0.0.1", 9);

        seeder.stopSharing(a);
        assertEquals(1, seeder.getSharedFileCount());
        assertArrayEquals(slice(dataB, 1), requestChunk(port, infoB.fileHash(), 1));

        seeder.stopSharing(b);
        assertEquals(0, seeder.getSharedFileCount());
        assertTrue(isClosed(port));

        seeder.stop();
    }

    @Test
    void changedFileContentReplacesTheStaleEntry() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        Path file = write("a.bin", randomBytes(1500, 7));
        SeederService.SharingInfo oldInfo = seeder.startSharing(file, "127.0.0.1", port, "127.0.0.1", 9);

        byte[] newData = randomBytes(900, 8);
        Files.write(file, newData);
        SeederService.SharingInfo newInfo = seeder.startSharing(file, "127.0.0.1", port, "127.0.0.1", 9);

        assertEquals(1, seeder.getSharedFileCount());
        assertArrayEquals(slice(newData, 0), requestChunk(port, newInfo.fileHash(), 0));
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(HexFormat.of().parseHex(oldInfo.fileHash()));
            out.writeInt(-1);
            out.flush();
            assertEquals(-1, socket.getInputStream().read());
        }

        seeder.stop();
    }

    @Test
    void onlyFilesInsideTheStorageFolderAreMarkedManaged() throws Exception {
        SeederService seeder = newSeeder();
        Path storage = Files.createDirectories(dir.resolve("storage"));
        ReflectionTestUtils.setField(seeder, "storageDirectory", storage.toString());
        int port = freePort();
        Path inside = Files.write(storage.resolve("in.bin"), randomBytes(50, 9));
        Path outside = write("out.bin", randomBytes(60, 10));

        assertTrue(seeder.startSharing(inside, "127.0.0.1", port, "127.0.0.1", 9).managed());
        assertFalse(seeder.startSharing(outside, "127.0.0.1", port, "127.0.0.1", 9).managed());

        seeder.stop();
    }

    @Test
    void stopSharingByHashEndsOnlyThatShareAndKeepsTheFile() throws Exception {
        SeederService seeder = newSeeder();
        int port = freePort();
        Path a = write("a.bin", randomBytes(300, 11));
        Path b = write("b.bin", randomBytes(300, 12));
        SeederService.SharingInfo infoA = seeder.startSharing(a, "127.0.0.1", port, "127.0.0.1", 9);
        seeder.startSharing(b, "127.0.0.1", port, "127.0.0.1", 9);

        assertEquals(1, seeder.stopSharingByHash(infoA.fileHash()));
        assertEquals(0, seeder.stopSharingByHash(infoA.fileHash()));
        assertEquals(1, seeder.getSharedFileCount());
        assertTrue(Files.exists(a), "stopping a share must not delete the file");

        seeder.stop();
    }

    private SeederService newSeeder() {
        SeederService seeder = new SeederService();
        ReflectionTestUtils.setField(seeder, "chunkSize", CHUNK);
        return seeder;
    }

    private Path write(String name, byte[] data) throws IOException {
        return Files.write(dir.resolve(name), data);
    }

    private static byte[] randomBytes(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }

    private static byte[] slice(byte[] data, int chunk) {
        int from = chunk * CHUNK;
        int to = Math.min(data.length, from + CHUNK);
        return java.util.Arrays.copyOfRange(data, from, to);
    }

    private static int requestChunkCount(int port, String hash) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(HexFormat.of().parseHex(hash));
            out.writeInt(-1);
            out.flush();
            return new DataInputStream(socket.getInputStream()).readInt();
        }
    }

    private static byte[] requestChunk(int port, String hash, int chunk) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(HexFormat.of().parseHex(hash));
            out.writeInt(chunk);
            out.flush();
            return socket.getInputStream().readAllBytes();
        }
    }

    private static boolean isClosed(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
