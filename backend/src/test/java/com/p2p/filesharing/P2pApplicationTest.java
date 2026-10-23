package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "p2p.tracker.udp-port=0",
                "p2p.storage-dir=target/test-data"
        })
class P2pApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void startsHttpApiAndEmbeddedTracker() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/status", Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void sharingByPathIsDisabledOutsideTheDesktopApp() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/files/local", Map.of("path", "/tmp/anything"), Map.class);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void uploadsFileAndReturnsItsSha256() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource upload = new ByteArrayResource(
                "browser upload".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "browser.txt";
            }
        };
        body.add("file", upload);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/files", new HttpEntity<>(body, headers), Map.class);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("browser.txt", response.getBody().get("fileName"));
        assertTrue(response.getBody().get("fileHash").toString().matches("[0-9a-f]{64}"));
    }

    @Test
    void deletesUploadedFileFromStorage() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource upload = new ByteArrayResource("delete me".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "delete-me.txt";
            }
        };
        body.add("file", upload);
        restTemplate.postForEntity("/api/files", new HttpEntity<>(body, headers), Map.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/files?fileName=delete-me.txt",
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(Files.exists(Path.of("target/test-data/delete-me.txt")));
    }

    @Test
    void deletingSeededFileStopsItsSeeder() throws Exception {
        Path file = Path.of("target/test-data/delete-seeded.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "seeded file");

        int peerPort;
        try (ServerSocket availablePort = new ServerSocket(0)) {
            peerPort = availablePort.getLocalPort();
        }

        ResponseEntity<Map> seedResponse = restTemplate.postForEntity(
                "/api/seeders",
                Map.of(
                        "fileName", "delete-seeded.txt",
                        "peerIp", "127.0.0.1",
                        "peerPort", peerPort,
                        "trackerHost", "127.0.0.1",
                        "trackerPort", 9),
                Map.class);
        assertEquals(201, seedResponse.getStatusCode().value());
        assertEquals(1, restTemplate.getForEntity("/api/seeders", List.class).getBody().size());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/files?fileName=delete-seeded.txt",
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(204, deleteResponse.getStatusCode().value());
        assertEquals(0, restTemplate.getForEntity("/api/seeders", List.class).getBody().size());
        assertFalse(Files.exists(file));
    }
}