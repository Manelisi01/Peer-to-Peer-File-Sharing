package com.p2p.filesharing;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    public P2pController(
            DownloadService downloadService,
            SeederService seederService,
            TrackerService trackerService) {
        this.downloadService = downloadService;
        this.seederService = seederService;
        this.trackerService = trackerService;
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