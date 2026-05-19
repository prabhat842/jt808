package com.example.jt808sim.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LongSummaryStatistics;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsRegistry {
    private final Instant startedAt = Instant.now();
    private final AtomicLong configuredTerminals = new AtomicLong();
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong authenticatedSessions = new AtomicLong();
    private final AtomicLong activeMediaSessions = new AtomicLong();
    private final LongAdder outboundMessages = new LongAdder();
    private final LongAdder outboundFailures = new LongAdder();
    private final LongAdder skippedWrites = new LongAdder();
    private final LongAdder inboundMessages = new LongAdder();
    private final LongAdder locationReports = new LongAdder();
    private final LongAdder heartbeats = new LongAdder();
    private final LongAdder reconnectAttempts = new LongAdder();
    private final LongAdder invalidChecksum = new LongAdder();
    private final LongAdder connectionFailures = new LongAdder();
    private final LongAdder registrationFailures = new LongAdder();
    private final LongAdder mediaPackets = new LongAdder();
    private final LongAdder mediaBytes = new LongAdder();
    private final LongAdder mediaInboundPackets = new LongAdder();
    private final LongAdder mediaInboundBytes = new LongAdder();
    private final LongAdder mediaInboundAudioPackets = new LongAdder();
    private final LongAdder mediaInboundAudioBytes = new LongAdder();
    private final LongAdder mediaConnectionFailures = new LongAdder();
    private final Queue<Long> ackLatenciesMillis = new ConcurrentLinkedQueue<>();

    public Instant startedAt() {
        return startedAt;
    }

    public AtomicLong configuredTerminals() {
        return configuredTerminals;
    }

    public AtomicLong activeConnections() {
        return activeConnections;
    }

    public AtomicLong authenticatedSessions() {
        return authenticatedSessions;
    }

    public AtomicLong activeMediaSessions() {
        return activeMediaSessions;
    }

    public LongAdder outboundMessages() {
        return outboundMessages;
    }

    public LongAdder outboundFailures() {
        return outboundFailures;
    }

    public LongAdder skippedWrites() {
        return skippedWrites;
    }

    public LongAdder inboundMessages() {
        return inboundMessages;
    }

    public LongAdder locationReports() {
        return locationReports;
    }

    public LongAdder heartbeats() {
        return heartbeats;
    }

    public LongAdder reconnectAttempts() {
        return reconnectAttempts;
    }

    public LongAdder invalidChecksum() {
        return invalidChecksum;
    }

    public LongAdder connectionFailures() {
        return connectionFailures;
    }

    public LongAdder registrationFailures() {
        return registrationFailures;
    }

    public LongAdder mediaPackets() {
        return mediaPackets;
    }

    public LongAdder mediaBytes() {
        return mediaBytes;
    }

    public LongAdder mediaInboundPackets() {
        return mediaInboundPackets;
    }

    public LongAdder mediaInboundBytes() {
        return mediaInboundBytes;
    }

    public LongAdder mediaInboundAudioPackets() {
        return mediaInboundAudioPackets;
    }

    public LongAdder mediaInboundAudioBytes() {
        return mediaInboundAudioBytes;
    }

    public LongAdder mediaConnectionFailures() {
        return mediaConnectionFailures;
    }

    public void recordAckLatency(long millis) {
        ackLatenciesMillis.add(millis);
        while (ackLatenciesMillis.size() > 4096) {
            ackLatenciesMillis.poll();
        }
    }

    public AckStats ackStats() {
        ArrayDeque<Long> snapshot = new ArrayDeque<>(ackLatenciesMillis);
        if (snapshot.isEmpty()) {
            return new AckStats(0, 0);
        }
        LongSummaryStatistics summary = snapshot.stream().mapToLong(Long::longValue).summaryStatistics();
        int p95Index = Math.max(0, (int) Math.ceil(snapshot.size() * 0.95) - 1);
        long p95 = snapshot.stream().sorted(Comparator.naturalOrder()).skip(p95Index).findFirst().orElse(0L);
        return new AckStats((long) summary.getAverage(), p95);
    }

    public MemoryUsage heapUsage() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    }

    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }

    public record AckStats(long averageMillis, long p95Millis) {
    }
}
