package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramSocket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrackerServiceTest {

    @Test
    void startKeepsAppRunningWhenTrackerPortIsBusy() throws Exception {
        int port = getFreePort();
        try (DatagramSocket busySocket = new DatagramSocket(port)) {
            TrackerService service = new TrackerService();
            ReflectionTestUtils.setField(service, "udpPort", port);
            ReflectionTestUtils.setField(service, "enabled", true);

            service.start();

            assertFalse((boolean) ReflectionTestUtils.getField(service, "enabled"));
            assertNull(ReflectionTestUtils.getField(service, "serverSocket"));
        }
    }

    @Test
    void heartbeatRegistersPeerIfTrackerNoLongerHasIt() {
        TrackerService service = new TrackerService();
        ReflectionTestUtils.setField(service, "peerTimeoutMs", 60_000L);
        String hash = "a".repeat(64);

        ReflectionTestUtils.invokeMethod(service, "updatePeer",
                (Object) new String[] {"UPDATE", hash, "192.0.2.1", "7000"});

        assertEquals(List.of(new PeerAddress("192.0.2.1", 7000)), service.getPeers(hash));
    }

    @Test
    void reportsTrackerAvailableWhenItAnswersPing() throws Exception {
        int port = getFreePort();
        TrackerService service = new TrackerService();
        ReflectionTestUtils.setField(service, "udpPort", port);
        ReflectionTestUtils.setField(service, "enabled", true);
        service.start();

        try {
            assertTrue(service.isTrackerAvailable("127.0.0.1", port));
        } finally {
            service.stop();
        }
    }

    @Test
    void reportsTrackerUnavailableWhenItDoesNotAnswerPing() throws Exception {
        try (DatagramSocket silentTracker = new DatagramSocket(0)) {
            TrackerService service = new TrackerService();

            assertFalse(service.isTrackerAvailable("127.0.0.1", silentTracker.getLocalPort()));
        }
    }

    private int getFreePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
