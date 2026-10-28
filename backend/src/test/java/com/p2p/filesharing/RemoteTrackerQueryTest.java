package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RemoteTrackerQueryTest {
    @Test
    void canQueryRemoteTrackerWhenLocalTrackerDisabled() throws Exception {
        try (DatagramSocket fakeTracker = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            Thread responder = new Thread(() -> {
                try {
                    byte[] buffer = new byte[256];
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                    fakeTracker.receive(request);
                    byte[] response = "192.168.1.20:7000".getBytes(StandardCharsets.UTF_8);
                    fakeTracker.send(new DatagramPacket(response, response.length,
                            request.getAddress(), request.getPort()));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            responder.start();
            TrackerService service = new TrackerService();
            ReflectionTestUtils.setField(service, "enabled", false);
            var peers = service.query("a".repeat(64), "127.0.0.1", fakeTracker.getLocalPort());
            assertEquals(1, peers.size());
            assertEquals("192.168.1.20", peers.get(0).getAddress().getHostAddress());
            responder.join(2000);
        }
    }
}
