package com.p2p.filesharing;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class DownloadJob {

    private final String id = UUID.randomUUID().toString();
    private final String targetFile;
    private final Instant createdAt = Instant.now();
    private final AtomicInteger progress = new AtomicInteger();
    private volatile String status = "QUEUED";
    private volatile String message = "Download queued.";
    private volatile Future<?> task;
    // Set once the file is being moved into place; cancelling is no longer possible after this.
    private boolean finalizing;

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

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    // A cancelled job stays cancelled: late updates from the worker thread are ignored.
    synchronized void update(String status, String message) {
        if (isCancelled()) {
            return;
        }
        this.status = status;
        this.message = message;
    }

    /** Marks the job cancelled and interrupts its worker. Returns false if it is too late. */
    synchronized boolean cancel(String message) {
        boolean active = switch (status) {
            case "QUEUED", "DISCOVERING", "DOWNLOADING", "VERIFYING" -> true;
            default -> false;
        };
        if (!active || finalizing) {
            return false;
        }
        this.status = "CANCELLED";
        this.message = message;
        Future<?> running = task;
        if (running != null) {
            running.cancel(true);
        }
        return true;
    }

    /** Called right before the finished file is moved into place. False means it was cancelled. */
    synchronized boolean beginFinalize() {
        if (isCancelled()) {
            return false;
        }
        finalizing = true;
        return true;
    }

    void attach(Future<?> task) {
        this.task = task;
    }

    void setProgress(int value) {
        progress.set(value);
    }
}