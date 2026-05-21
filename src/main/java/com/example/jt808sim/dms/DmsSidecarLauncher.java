package com.example.jt808sim.dms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launches and monitors the DMS sidecar subprocess (dms_server.py).
 * Restarts it automatically if it exits unexpectedly.
 *
 * The sidecar is a vehicle-side component: it runs whenever the
 * simulator runs, independent of jt808-server or rtvs availability.
 */
public final class DmsSidecarLauncher implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DmsSidecarLauncher.class);

    private final String scriptPath;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "dms-sidecar-watchdog"); t.setDaemon(true); return t; });
    private volatile Process process;

    public DmsSidecarLauncher(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public void start() {
        launch();
        // check every 5 seconds and restart if dead
        watchdog.scheduleWithFixedDelay(this::checkAndRestart, 5, 5, TimeUnit.SECONDS);
        log.info("DMS sidecar launcher started (script={})", scriptPath);
    }

    private void launch() {
        if (closed.get()) return;
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath);
            pb.inheritIO(); // sidecar logs go to simulator stdout/stderr
            process = pb.start();
            log.info("DMS sidecar started (pid={})", process.pid());
        } catch (IOException e) {
            log.error("failed to start DMS sidecar: {}", e.getMessage());
        }
    }

    private void checkAndRestart() {
        if (closed.get()) return;
        Process p = process;
        if (p == null || !p.isAlive()) {
            log.warn("DMS sidecar exited — restarting");
            launch();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        watchdog.shutdownNow();
        Process p = process;
        if (p != null && p.isAlive()) {
            p.destroy();
            log.info("DMS sidecar stopped");
        }
    }
}
