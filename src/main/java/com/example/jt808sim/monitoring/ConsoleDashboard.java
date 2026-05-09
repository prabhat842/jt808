package com.example.jt808sim.monitoring;

import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleDashboard implements AutoCloseable {
    private final MetricsRegistry metrics;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dashboard");
        thread.setDaemon(true);
        return thread;
    });
    private long lastOutbound;
    private long lastInbound;
    private long lastLocation;
    private long lastHeartbeat;
    private long lastMediaPackets;
    private long lastMediaBytes;

    public ConsoleDashboard(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::render, 1, 1, TimeUnit.SECONDS);
    }

    private void render() {
        long outbound = metrics.outboundMessages().sum();
        long inbound = metrics.inboundMessages().sum();
        long locations = metrics.locationReports().sum();
        long heartbeats = metrics.heartbeats().sum();
        long mediaPackets = metrics.mediaPackets().sum();
        long mediaBytes = metrics.mediaBytes().sum();
        MetricsRegistry.AckStats ack = metrics.ackStats();
        MemoryUsage heap = metrics.heapUsage();

        String text = """
                JT808/JT1078 Fleet Simulator
                --------------------------------------------------
                Configured terminals : %d
                Connected terminals  : %d
                Authenticated        : %d
                Msg/sec outbound     : %d
                Msg/sec inbound      : %d
                Location reports/sec : %d
                Heartbeats/sec       : %d
                Media sessions       : %d
                Media outbound       : %d pkt/s, %.2f MB/s
                Avg ack latency      : %d ms
                P95 ack latency      : %d ms
                Reconnect attempts   : %d
                Registration failures: %d
                Connection failures  : %d
                Media conn failures  : %d
                Invalid checksums    : %d
                Heap used            : %d MB
                Uptime               : %s
                --------------------------------------------------
                """.formatted(
                metrics.configuredTerminals().get(),
                metrics.activeConnections().get(),
                metrics.authenticatedSessions().get(),
                outbound - lastOutbound,
                inbound - lastInbound,
                locations - lastLocation,
                heartbeats - lastHeartbeat,
                metrics.activeMediaSessions().get(),
                mediaPackets - lastMediaPackets,
                (mediaBytes - lastMediaBytes) / 1_048_576.0,
                ack.averageMillis(),
                ack.p95Millis(),
                metrics.reconnectAttempts().sum(),
                metrics.registrationFailures().sum(),
                metrics.connectionFailures().sum(),
                metrics.mediaConnectionFailures().sum(),
                metrics.invalidChecksum().sum(),
                heap.getUsed() / 1_048_576,
                formatDuration(metrics.uptime()));
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println(text);
        lastOutbound = outbound;
        lastInbound = inbound;
        lastLocation = locations;
        lastHeartbeat = heartbeats;
        lastMediaPackets = mediaPackets;
        lastMediaBytes = mediaBytes;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
