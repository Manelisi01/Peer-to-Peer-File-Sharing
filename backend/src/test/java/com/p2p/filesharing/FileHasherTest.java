package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHasherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void calculatesSha256ForFileContents() throws Exception {
        Path file = temporaryDirectory.resolve("sample.txt");
        Files.writeString(file, "hello world");

        assertEquals(
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                FileHasher.calculateSha256(file));
    }

    @Test
    void convertsBytesToHexAndBack() {
        byte[] bytes = {0, (byte) 0xa1, (byte) 0xff};

        String hex = FileHasher.toHexString(bytes);

        assertEquals("00a1ff", hex);
        assertArrayEquals(bytes, FileHasher.fromHexString(hex));
    }
}