package com.example.jt808sim.fleet;

import com.example.jt808sim.physics.Coordinate;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fixed-capacity ring buffer of recent location snapshots.
 *
 * Used for two purposes:
 *   0x0704 — bulk upload of buffered locations after connectivity loss
 *   0x0705 — accident suspect report (last N seconds of positions)
 */
public class LocationBuffer {

    private static final int CAPACITY = 120; // ~2 minutes at 1Hz

    /**
     * Immutable snapshot of a single location report, carrying only what is
     * needed to encode the 28-byte basic location block (Table 23, JT808-2013).
     */
    public record LocationSnapshot(
            long       alarmWord,
            long       statusWord,
            Coordinate coordinate,
            double     speedKph,
            int        heading,
            int        altitudeMeters,
            Instant    time
    ) {}

    private final Deque<LocationSnapshot> ring = new ArrayDeque<>(CAPACITY);

    // ── Write ─────────────────────────────────────────────────────────────────

    public synchronized void add(LocationSnapshot snapshot) {
        if (ring.size() >= CAPACITY) ring.pollFirst();
        ring.addLast(snapshot);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Drains and returns all buffered snapshots, clearing the buffer. Used for 0x0704. */
    public synchronized List<LocationSnapshot> drain() {
        List<LocationSnapshot> result = new ArrayList<>(ring);
        ring.clear();
        return result;
    }

    /** Returns (without clearing) all snapshots captured within the last {@code seconds}. Used for 0x0705. */
    public synchronized List<LocationSnapshot> recentSeconds(int seconds) {
        Instant cutoff = Instant.now().minusSeconds(seconds);
        List<LocationSnapshot> result = new ArrayList<>();
        for (LocationSnapshot s : ring) {
            if (!s.time().isBefore(cutoff)) result.add(s);
        }
        return result;
    }

    public synchronized int size() { return ring.size(); }
}
