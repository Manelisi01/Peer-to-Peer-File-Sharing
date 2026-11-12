package com.p2p.filesharing;

public record SharingInfo(
        String fileName,
        String fileHash,
        String peerIp,
        int peerPort,
        long fileSize,
        int totalChunks,
        boolean managed) {
}
