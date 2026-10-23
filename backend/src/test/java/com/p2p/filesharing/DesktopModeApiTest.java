package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "p2p.tracker.udp-port=0",
                "p2p.storage-dir=target/desktop-test-data",
                "p2p.api-token=test-secret",
                "p2p.allow-path-sharing=true"
        })
class DesktopModeApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @TempDir
    Path dir;

    @Test
    void apiRequiresTheDesktopToken() {
        ResponseEntity<Map> refused = restTemplate.getForEntity("/api/status", Map.class);
        ResponseEntity<Map> allowed = restTemplate.exchange(
                "/api/status", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertEquals(403, refused.getStatusCode().value());
        assertEquals(200, allowed.getStatusCode().value());
    }

    @Test
    void sharesAFileWhereItIsAndStoppingNeverDeletesIt() throws Exception {
        Path file = Files.writeString(dir.resolve("movie.bin"), "shared in place");

        ResponseEntity<Map> hashed = post("/api/files/local", Map.of("path", file.toString()));
        assertEquals(200, hashed.getStatusCode().value());
        String hash = (String) hashed.getBody().get("fileHash");
        assertEquals(64, hash.length());

        ResponseEntity<Map> seeded = post("/api/seeders/local", Map.of(
                "path", file.toString(),
                "peerIp", "127.0.0.1",
                "peerPort", freePort(),
                "trackerHost", "127.0.0.1",
                "trackerPort", 9));
        assertEquals(201, seeded.getStatusCode().value());
        assertEquals(hash, seeded.getBody().get("fileHash"));
        assertEquals(false, seeded.getBody().get("managed"));

        ResponseEntity<List> listed = restTemplate.exchange(
                "/api/seeders", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
        assertEquals(1, listed.getBody().size());

        ResponseEntity<Void> stopped = restTemplate.exchange(
                "/api/seeders?hash=" + hash, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertEquals(204, stopped.getStatusCode().value());
        assertTrue(Files.exists(file), "stopping a share must leave the original file alone");

        ResponseEntity<Map> again = restTemplate.exchange(
                "/api/seeders?hash=" + hash, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);
        assertEquals(404, again.getStatusCode().value());
    }

    @Test
    void rejectsRelativeAndMissingPaths() {
        assertEquals(400, post("/api/files/local", Map.of("path", "relative/file.txt"))
                .getStatusCode().value());
        assertEquals(404, post("/api/files/local", Map.of("path", dir.resolve("missing.bin").toString()))
                .getStatusCode().value());
    }

    private ResponseEntity<Map> post(String url, Map<String, Object> body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiTokenFilter.HEADER, "test-secret");
        return headers;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
