package com.p2p.filesharing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);
    private static final int SOCKET_TIMEOUT_MS = 10000;
    private static final int MAX_DOWNLOAD_THREADS = 8;
    private static final String TEMP_DIR = ".downloads";

    @Value("${p2p.storage-dir:./data}")
    private String storageDirectory;

    // false = downloads are temporary: handed to the browser, then deleted from the server.
    @Value("${p2p.keep-downloads:false}")
    private boolean keepDownloads;

    @Value("${p2p.download-ttl-minutes:30}")
    private long downloadTtlMinutes;

    @Value("${p2p.chunk-size:524288}")
    private int chunkSize;

    private final TrackerService trackerService;
    private final SeederService seederService;
    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Path> downloadPaths = new ConcurrentHashMap<>();
    private final ExecutorService jobExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public DownloadService(TrackerService trackerService, SeederService seederService) {
        this.trackerService = trackerService;
        this.seederService = seederService;
    }

    public DownloadJob startDownload(DownloadRequest request) {
        Path requested = resolveStoragePath(request.targetFile());
        String displayName = storageRelativePath(requested);
        DownloadJob job = new DownloadJob(displayName);
        // Temporary downloads get their own folder so they can never overwrite a library file.
        Path destination = keepDownloads
                ? requested
                : resolveStoragePath(TEMP_DIR + "/" + job.getId() + "/" + displayName);
        downloadPaths.put(job.getId(), destination);
        jobs.put(job.getId(), job);
        jobExecutor.submit(() -> runDownload(job, destination, request));
        return job;
    }

    public DownloadJob getJob(String id) {
        return jobs.get(id);
    }

    /** Path of a finished download, or null if the job is unknown, unfinished or the file is gone. */
    public Path getCompletedFile(String id) {
        DownloadJob job = jobs.get(id);
        Path file = downloadPaths.get(id);
        if (job == null || file == null || !"COMPLETED".equals(job.getStatus())) {
            return null;
        }
        return Files.isRegularFile(file) ? file : null;
    }

    /** Called once the browser has received the file. No-op when downloads are kept. */
    public void finishDelivery(String id) {
        discardDownload(id, "Saved to your device and removed from the server.");
    }

    private void discardDownload(String id, String message) {
        if (keepDownloads) {
            return;
        }
        Path file = downloadPaths.remove(id);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && !parent.getFileName().toString().equals(TEMP_DIR)) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException e) {
            log.warn("Could not remove temporary download {}", file, e);
        }
        DownloadJob job = jobs.get(id);
        if (job != null) {
            job.update(job.getStatus(), message);
        }
    }

    @PostConstruct
    void clearLeftoverTemporaryDownloads() {
        if (keepDownloads) {
            return;
        }
        Path dir = resolveStoragePath(TEMP_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Could not clear {}", dir, e);
        }
    }

    public String hashFile(String fileName) throws IOException {
        return FileHasher.calculateSha256(resolveStoragePath(fileName));
    }

    public void deleteFile(String fileName) throws IOException {
        Path file = resolveStoragePath(fileName);
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString());
        }
        seederService.stopSharing(file);
        Files.delete(file);
    }

    public FileInfo storeUploadedFile(MultipartFile upload) throws IOException {
        String originalName = upload.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("A file name is required.");
        }
        String safeName = originalName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..")) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        Path destination = resolveStoragePath(safeName);
        Files.createDirectories(destination.getParent());
        try (InputStream input = upload.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return new FileInfo(safeName, upload.getSize(), FileHasher.calculateSha256(destination));
    }

    public Path resolveStoragePath(String fileName) {
        Path root = Paths.get(storageDirectory).toAbsolutePath().normalize();
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("File path must remain inside the storage directory.");
        }
        return path;
    }

    @PreDestroy
    void stop() {
        jobExecutor.shutdownNow();
        cleaner.shutdownNow();
        clearLeftoverTemporaryDownloads();
    }

    private void runDownload(DownloadJob job, Path destination, DownloadRequest request) {
        Path temporaryFile = null;
        try {
            job.update("DISCOVERING", "Querying tracker for seeders.");
            List<InetSocketAddress> seeders = trackerService.query(
                    request.fileHash(), request.trackerHost(), request.trackerPort());
            if (seeders.isEmpty()) {
                throw new IOException("No seeders found for this file hash.");
            }

            int totalChunks = getTotalChunks(seeders.get(0));
            Path parent = destination.getParent();
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "p2p-download-", ".part");

            job.update("DOWNLOADING", "Downloading from " + seeders.size() + " seeder(s).");
            downloadChunks(seeders, totalChunks, temporaryFile, job);

            job.update("VERIFYING", "Checking the completed file hash.");
            String actualHash = FileHasher.calculateSha256(temporaryFile);
            if (!actualHash.equalsIgnoreCase(request.fileHash())) {
                throw new IOException("SHA-256 verification failed.");
            }

            try {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            temporaryFile = null;

            String message = "Download complete and verified.";
            if (keepDownloads) {
                try {
                    seederService.startSharing(destination, request.peerIp(), request.peerPort(),
                            request.trackerHost(), request.trackerPort());
                } catch (Exception e) {
                    message += " Could not start seeding: " + e.getMessage();
                    log.warn("Downloaded file could not be started as a seeder", e);
                }
            } else {
                message += " Save it to your device within " + downloadTtlMinutes + " minutes.";
                cleaner.schedule(
                        () -> discardDownload(job.getId(), "Expired and removed from the server."),
                        downloadTtlMinutes, TimeUnit.MINUTES);
            }
            job.setProgress(100);
            job.update("COMPLETED", message);
        } catch (Exception e) {
            log.warn("Download job {} failed", job.getId(), e);
            job.update("FAILED", e.getMessage() == null ? "Download failed." : e.getMessage());
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    log.warn("Could not remove temporary download file {}", temporaryFile, e);
                }
            }
        }
    }

    private void downloadChunks(
            List<InetSocketAddress> seeders,
            int totalChunks,
            Path temporaryFile,
            DownloadJob job) throws Exception {
        if (totalChunks == 0) {
            return;
        }

        int threadCount = Math.min(MAX_DOWNLOAD_THREADS, totalChunks);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ExecutorCompletionService<ChunkResult> completion =
                new ExecutorCompletionService<>(executor);

        try (RandomAccessFile output = new RandomAccessFile(temporaryFile.toFile(), "rw")) {
            output.setLength((long) totalChunks * chunkSize);
            int nextChunk = 0;
            int completed = 0;
            int lastChunkLength = -1;

            while (nextChunk < threadCount) {
                submitChunk(completion, seeders, nextChunk++);
            }

            while (completed < totalChunks) {
                ChunkResult result = completion.take().get();
                if (result.data.length == 0 || result.data.length > chunkSize
                        || (result.chunkNumber < totalChunks - 1
                        && result.data.length != chunkSize)) {
                    throw new IOException("Seeder returned an invalid chunk size.");
                }
                output.seek((long) result.chunkNumber * chunkSize);
                output.write(result.data);
                if (result.chunkNumber == totalChunks - 1) {
                    lastChunkLength = result.data.length;
                }
                completed++;
                job.setProgress((int) (completed * 100.0 / totalChunks));
                if (nextChunk < totalChunks) {
                    submitChunk(completion, seeders, nextChunk++);
                }
            }

            if (lastChunkLength < 0) {
                throw new IOException("Final chunk was not downloaded.");
            }
            output.setLength((long) (totalChunks - 1) * chunkSize + lastChunkLength);
        } finally {
            executor.shutdownNow();
        }
    }

    private void submitChunk(
            ExecutorCompletionService<ChunkResult> completion,
            List<InetSocketAddress> seeders,
            int chunkNumber) {
        completion.submit(() -> {
            IOException lastError = null;
            for (int attempt = 0; attempt < seeders.size(); attempt++) {
                InetSocketAddress seeder = seeders.get((chunkNumber + attempt) % seeders.size());
                try {
                    return new ChunkResult(chunkNumber, downloadChunk(seeder, chunkNumber));
                } catch (IOException e) {
                    lastError = e;
                }
            }
            throw new IOException("Could not download chunk " + chunkNumber
                    + " from any available seeder.", lastError);
        });
    }

    private int getTotalChunks(InetSocketAddress seeder) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(seeder, SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.writeInt(-1);
            output.flush();
            int totalChunks = input.readInt();
            if (totalChunks < 0) {
                throw new IOException("Seeder returned an invalid chunk count.");
            }
            return totalChunks;
        } catch (SocketTimeoutException e) {
            throw new IOException("Seeder timed out while reporting its chunk count.", e);
        }
    }

    private byte[] downloadChunk(InetSocketAddress seeder, int chunkNumber) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(seeder, SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(chunkNumber);
            output.flush();

            try (InputStream input = socket.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream(chunkSize)) {
                byte[] data = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(data)) != -1) {
                    buffer.write(data, 0, bytesRead);
                    if (buffer.size() > chunkSize) {
                        throw new IOException("Seeder sent more data than the chunk size.");
                    }
                }
                return buffer.toByteArray();
            }
        }
    }

    private String storageRelativePath(Path path) {
        return Paths.get(storageDirectory).toAbsolutePath().normalize()
                .relativize(path).toString();
    }

    private record ChunkResult(int chunkNumber, byte[] data) {
    }

    public record FileInfo(String fileName, long fileSize, String fileHash) {
    }
}