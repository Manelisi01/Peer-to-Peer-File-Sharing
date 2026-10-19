package com.p2p.filesharing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.DatagramSocket;
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

    private int getFreePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
