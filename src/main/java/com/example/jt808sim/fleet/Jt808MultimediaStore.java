package com.example.jt808sim.fleet;

import com.example.jt808sim.physics.Coordinate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store for JT808 multimedia items (§7.9, JT808-2013).
 *
 * Distinct from the JT1078 TerminalMediaCatalog (which handles streaming video).
 * This store tracks image/audio snapshots captured by 0x8801 camera shots,
 * alarm-triggered events, and 0x8804 audio recording commands.
 *
 * Items are queried by 0x8802 and individually uploaded via 0x0801.
 */
public class Jt808MultimediaStore {
    private static final AtomicLong ID_GEN = new AtomicLong(1_000_000);

    private final List<MultimediaItem> items = new ArrayList<>();

    // ── Data model ────────────────────────────────────────────────────────────

    /**
     * A single captured multimedia item.
     *
     * mediaType:  0=image  1=audio  2=video
     * formatCode: 0=JPEG   1=TIF    2=MP3   3=WAV   4=WMV
     * eventCode:  0=platform command  1=timing  2=robbery  3=collision/rollover
     */
    public record MultimediaItem(
            long      multimediaId,
            int       mediaType,
            int       formatCode,
            int       eventCode,
            int       channelId,
            Instant   captureTime,
            Coordinate capturePosition,
            double    captureSpeedKph
    ) {
        /** Synthetic payload bytes — a minimal JPEG SOI header for images, zeros for audio. */
        public byte[] syntheticPayload() {
            if (mediaType == 0) {
                // Minimal JPEG: SOI + SOF0 marker stub (20 bytes)
                return new byte[]{
                        (byte)0xFF, (byte)0xD8, // SOI
                        (byte)0xFF, (byte)0xE0, 0x00, 0x10, // APP0
                        0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
                        0x01, 0x01,                   // version
                        0x00, 0x00, 0x01, 0x00, 0x01, // density
                        0x00, 0x00,                   // thumbnail
                        (byte)0xFF, (byte)0xD9         // EOI
                };
            }
            return new byte[128]; // synthetic audio silence
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /** Adds a new multimedia item and returns its assigned ID. */
    public long add(int mediaType, int formatCode, int eventCode, int channelId,
                    Coordinate position, double speedKph) {
        long id = ID_GEN.getAndIncrement();
        items.add(new MultimediaItem(id, mediaType, formatCode, eventCode, channelId,
                Instant.now(), position, speedKph));
        return id;
    }

    /** Adds N snapshot items for the given channel and returns their IDs. */
    public List<Long> addSnapshots(int count, int channelId, int eventCode,
                                   Coordinate position, double speedKph) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(add(0, 0, eventCode, channelId, position, speedKph)); // 0=image 0=JPEG
        }
        return ids;
    }

    /** Returns all items matching the query criteria. */
    public List<MultimediaItem> query(int mediaType, int channelId, int eventCode,
                                      Instant startTime, Instant endTime) {
        return items.stream()
                .filter(item -> item.mediaType() == mediaType)
                .filter(item -> channelId == 0 || item.channelId() == channelId)
                .filter(item -> eventCode == 0 || item.eventCode() == eventCode)
                .filter(item -> startTime == null || !item.captureTime().isBefore(startTime))
                .filter(item -> endTime   == null || !item.captureTime().isAfter(endTime))
                .toList();
    }

    /** Returns the item with the given ID, or null if not found. */
    public MultimediaItem get(long id) {
        return items.stream().filter(i -> i.multimediaId() == id).findFirst().orElse(null);
    }

    /** Removes items by ID (for deleteAfterUpload). */
    public void remove(long id) {
        items.removeIf(i -> i.multimediaId() == id);
    }

    public int size() { return items.size(); }
}
