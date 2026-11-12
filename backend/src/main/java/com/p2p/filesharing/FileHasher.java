package com.p2p.filesharing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHasher {

    private FileHasher() {
    }

    public static String calculateSha256(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + file);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return toHexString(digest.digest());
    }

    public static String toHexString(byte[] values) {
        StringBuilder result = new StringBuilder(values.length * 2);

        for (byte value : values) {
            result.append(String.format("%02x", value & 0xff));
        }

        return result.toString();
    }

    public static byte[] fromHexString(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex value must contain pairs of characters.");
        }

        byte[] result = new byte[value.length() / 2];

        for (int i = 0; i < value.length(); i += 2) {
            String twoDigits = value.substring(i, i + 2);
            result[i / 2] = (byte) Integer.parseInt(twoDigits, 16);
        }

        return result;
    }
}