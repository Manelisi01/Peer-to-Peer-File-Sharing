package com.p2p.filesharing;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class P2pController {

    private final DownloadService downloadService;
    private final SeederService seederService;
    private final TrackerService trackerService;
    private final boolean pathSharingAllowed;

    public P2pController(
            DownloadService downloadService,
            SeederService seederService,
            TrackerService trackerService,
            @Value("${p2p.allow-path-sharing:false}") boolean allowPathSharing,
            @Value("${p2p.api-token:}") String apiToken) {
        this.downloadService = downloadService;
        this.seederService = seederService;
        this.trackerService = trackerService;
        // Sharing by path lets the caller pick any readable file, so it is only enabled when
        // the API is also protected by the desktop app's per-launch token.
        this.pathSharingAllowed = allowPathSharing && !apiToken.isBlank();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "UP", "sharedFiles", seederService.getSharedFileCount());
    }

    @GetMapping("/files/hash")
    public Map<String, String> hashFile(@RequestParam String fileName) throws IOException {
        return Map.of("fileName", fileName, "fileHash", downloadService.hashFile(fileName));
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DownloadService.FileInfo uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        return downloadService.storeUploadedFile(file);
    }

    @DeleteMapping("/files")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@RequestParam String fileName) throws IOException {
        try {
            downloadService.deleteFile(fileName);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file not found.");
        }
    }

    @PostMapping("/seeders")
    @ResponseStatus(HttpStatus.CREATED)
    public SeederService.SharingInfo startSharing(@Valid @RequestBody SeedRequest request)
            throws IOException {
        return seederService.startSharing(
                downloadService.resolveStoragePath(request.fileName()),
                request.peerIp(), request.peerPort(), request.trackerHost(), request.trackerPort());
    }

    /** Desktop app: hashes a file where it is, without copying it into storage. */
    @PostMapping("/files/local")
    public DownloadService.FileInfo hashLocalFile(@Valid @RequestBody LocalFileRequest request)
            throws IOException {
        Path file = requireLocalFile(request.path());
        return new DownloadService.FileInfo(
                file.getFileName().toString(), Files.size(file), FileHasher.calculateSha256(file));
    }

    /** Desktop app: shares a file from its current location. */
    @PostMapping("/seeders/local")
    @ResponseStatus(HttpStatus.CREATED)
    public SeederService.SharingInfo startSharingLocal(@Valid @RequestBody LocalSeedRequest request)
            throws IOException {
        Path file = requireLocalFile(request.path());
        return seederService.startSharing(
                file, request.peerIp(), request.peerPort(), request.trackerHost(), request.trackerPort());
    }

    /** Stops sharing a file. The file itself is never touched. */
    @DeleteMapping("/seeders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopSharing(@RequestParam String hash) {
        if (seederService.stopSharingByHash(hash) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That file is not being shared.");
        }
    }

    private Path requireLocalFile(String rawPath) {
        if (!pathSharingAllowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sharing files by path is only available in the desktop app.");
        }
        try {
            Path path = Paths.get(rawPath);
            if (!path.isAbsolute()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An absolute file path is required.");
            }
            Path real = path.toRealPath();
            if (!Files.isRegularFile(real) || !Files.isReadable(real)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or not readable.");
            }
            return real;
        } catch (InvalidPathException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or not readable.");
        }
    }

    @GetMapping("/peers")
    public List<PeerAddress> peers(@RequestParam String hash) {
        return trackerService.getPeers(hash);
    }

    @GetMapping("/seeders")
    public List<SeederService.SharingInfo> seeders() {
        return seederService.getSharedFiles();
    }

    @PostMapping("/downloads")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DownloadJob startDownload(@Valid @RequestBody DownloadRequest request) {
        return downloadService.startDownload(request);
    }

    @GetMapping("/downloads/{id}")
    public DownloadJob downloadStatus(@PathVariable String id) {
        DownloadJob job = downloadService.getJob(id);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Download job not found.");
        }
        return job;
    }

    @PostMapping("/downloads/{id}/cancel")
    public DownloadJob cancelDownload(@PathVariable String id) {
        DownloadJob job = downloadService.cancelDownload(id);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Download job not found.");
        }
        return job;
    }

    /** Streams a completed download to the browser; it is then deleted from the server. */
    @GetMapping("/downloads/{id}/file")
    public ResponseEntity<StreamingResponseBody> downloadedFile(@PathVariable String id)
            throws IOException {
        Path file = downloadService.getCompletedFile(id);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Completed file not found.");
        }
        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(out);
            }
            // Only reached if the whole file was written; an interrupted transfer keeps the
            // file until the expiry timer removes it, so the user can retry.
            downloadService.finishDelivery(id);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(body);
    }
}