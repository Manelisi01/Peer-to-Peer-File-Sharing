package com.p2p.filesharing;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
}