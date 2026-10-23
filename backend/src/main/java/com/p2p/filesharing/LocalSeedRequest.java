package com.p2p.filesharing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LocalSeedRequest(
        @NotBlank String path,
        @NotBlank String peerIp,
        @Min(1) @Max(65535) int peerPort,
        @NotBlank String trackerHost,
        @Min(1) @Max(65535) int trackerPort) {
}
