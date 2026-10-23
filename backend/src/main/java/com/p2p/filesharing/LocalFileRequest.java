package com.p2p.filesharing;

import jakarta.validation.constraints.NotBlank;

public record LocalFileRequest(@NotBlank String path) {
}
