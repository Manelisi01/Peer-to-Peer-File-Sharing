package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

class DownloadJobTest {

    @Test
    void attachingTaskAfterCancellationCancelsIt() {
        DownloadJob job = new DownloadJob("file.txt");
        FutureTask<Void> task = new FutureTask<>(() -> null);

        assertTrue(job.cancel("Download cancelled."));
        job.attach(task);

        assertTrue(task.isCancelled());
    }

    @Test
    void snapshotKeepsStatusAndMessageFromSameUpdate() {
        DownloadJob job = new DownloadJob("file.txt");
        job.update("DOWNLOADING", "Downloading file.");

        DownloadJob.Snapshot snapshot = job.snapshot();

        assertEquals("DOWNLOADING", snapshot.status());
        assertEquals("Downloading file.", snapshot.message());
    }
}
