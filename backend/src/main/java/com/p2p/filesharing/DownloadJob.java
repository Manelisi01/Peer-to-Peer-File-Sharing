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
    private JobState state = new JobState("QUEUED", "Download queued.");
    private Future<?> task;
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

    public synchronized String getStatus() {
        return state.status();
    }

    public synchronized String getMessage() {
        return state.message();
    }

    public synchronized boolean isCancelled() {
        return "CANCELLED".equals(state.status());
    }

    public synchronized Snapshot snapshot() {
        JobState current = state;
        return new Snapshot(id, targetFile, createdAt, progress.get(),
                current.status(), current.message());
    }

    // A cancelled job stays cancelled: late updates from the worker thread are ignored.
    synchronized void update(String status, String message) {
        if ("CANCELLED".equals(state.status())) {
            return;
        }
        state = new JobState(status, message);
    }

    /** Marks the job cancelled and interrupts its worker. Returns false if it is too late. */
    synchronized boolean cancel(String message) {
        String currentStatus = state.status();
        boolean active = "QUEUED".equals(currentStatus)
                || "DISCOVERING".equals(currentStatus)
                || "DOWNLOADING".equals(currentStatus)
                || "VERIFYING".equals(currentStatus);

        if (!active || finalizing) {
            return false;
        }

        state = new JobState("CANCELLED", message);
        Future<?> running = task;
        if (running != null) {
            running.cancel(true);
        }
        return true;
    }

    /** Called right before the finished file is moved into place. False means it was cancelled. */
    synchronized boolean beginFinalize() {
        if ("CANCELLED".equals(state.status())) {
            return false;
        }
        finalizing = true;
        return true;
    }

    synchronized void attach(Future<?> task) {
        this.task = task;
        if ("CANCELLED".equals(state.status())) {
            task.cancel(true);
        }
    }

    synchronized void setProgress(int value) {
        progress.set(value);
    }

    public record Snapshot(
            String id,
            String targetFile,
            Instant createdAt,
            int progress,
            String status,
            String message) {
    }

    private record JobState(String status, String message) {
    }
}