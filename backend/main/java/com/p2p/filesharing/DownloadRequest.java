package com.p2p.filesharing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DownloadRequest(
        @NotBlank String targetFile,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String fileHash,
        @NotBlank String trackerHost,
        @Min(1) @Max(65535) int trackerPort,
        @NotBlank String peerIp,
        @Min(1) @Max(65535) int peerPort) {
}