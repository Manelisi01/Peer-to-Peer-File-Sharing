package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}