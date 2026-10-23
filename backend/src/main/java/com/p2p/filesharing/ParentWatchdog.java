package com.p2p.filesharing;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lets the desktop app guarantee that no backend is left running after it closes or crashes.
 *
 * The desktop app keeps this process's standard input open and never writes to it. When the
 * app exits for any reason the operating system closes that pipe, the read below returns
 * end-of-file, and the backend shuts itself down (releasing its seeding port).
 *
 * Off by default, so running the backend from a terminal is unaffected.
 */
@Component
public class ParentWatchdog {

    private static final Logger log = LoggerFactory.getLogger(ParentWatchdog.class);

    @Value("${p2p.exit-on-stdin-close:false}")
    private boolean enabled;

    @PostConstruct
    void start() {
        if (!enabled) {
            return;
        }
        Thread watchdog = new Thread(() -> {
            try {
                while (System.in.read() != -1) {
                    // Discard anything received; only end-of-file matters.
                }
            } catch (IOException e) {
                // A broken pipe means the parent is gone as well.
            }
            log.info("Desktop app closed; shutting down the backend.");
            System.exit(0);
        }, "parent-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
