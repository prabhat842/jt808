package com.example.jt808sim;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.dms.DmsSidecarLauncher;
import com.example.jt808sim.fleet.FleetManager;
import com.example.jt808sim.monitoring.ConsoleDashboard;
import com.example.jt808sim.monitoring.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Path configPath = parseConfigPath(args);
        FleetConfig config = FleetConfig.load(configPath);
        MetricsRegistry metrics = new MetricsRegistry();
        FleetManager fleetManager = new FleetManager(config, metrics);
        ConsoleDashboard dashboard = new ConsoleDashboard(metrics);

        // Auto-launch DMS sidecar if configured — camera is a vehicle component,
        // it runs independently of jt808-server and rtvs
        DmsSidecarLauncher dmsSidecar = buildDmsSidecar(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dashboard.close();
            fleetManager.close();
            if (dmsSidecar != null) dmsSidecar.close();
        }, "shutdown"));

        log.info("starting simulator with config {}", configPath);

        if (dmsSidecar != null) {
            dmsSidecar.start();
            // give the sidecar a moment to open the camera before FFmpeg tries to read it
            Thread.sleep(2000);
        }

        dashboard.start();
        fleetManager.start();
        new CountDownLatch(1).await();
    }

    private static DmsSidecarLauncher buildDmsSidecar(FleetConfig config) {
        FleetConfig.DmsConfig dms = config.getDms();
        if (!dms.isEnabled() || dms.getSidecarScript() == null || dms.getSidecarScript().isBlank()) {
            return null;
        }
        return new DmsSidecarLauncher(dms.getSidecarScript());
    }

    private static Path parseConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        if (args.length == 1 && !args[0].startsWith("--")) {
            return Path.of(args[0]);
        }
        if (Arrays.asList(args).contains("--help")) {
            System.out.println("Usage: java -jar target/jt808-fleet-simulator.jar --config config/fleet.json");
            System.exit(0);
        }
        return Path.of("config/fleet.json");
    }
}
