package com.p2p.filesharing;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DownloadJob {

    private final String id = UUID.randomUUID().toString();
    private final String targetFile;
    private final Instant createdAt = Instant.now();
    private final AtomicInteger progress = new AtomicInteger();
    private volatile String status = "QUEUED";
    private volatile String message = "Download queued.";

    public DownloadJob(String targetFile) {
        this.targetFile = targetFile;
    }

    public String getId() {
        return id;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getProgress() {
        return progress.get();
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    void update(String status, String message) {
        this.status = status;
        this.message = message;
    }

    void setProgress(int value) {
        progress.set(value);
    }
}