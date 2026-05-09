package com.example.jt808sim;

import com.example.jt808sim.config.FleetConfig;
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dashboard.close();
            fleetManager.close();
        }, "shutdown"));

        log.info("starting simulator with config {}", configPath);
        dashboard.start();
        fleetManager.start();
        new CountDownLatch(1).await();
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
