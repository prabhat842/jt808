package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalMediaCatalogTest {
    @Test
    void queryFiltersByRequestedTimeWindowAndAlarmFlags() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 10, 10, 0, 0);
        RecordedMediaResource first = new RecordedMediaResource(
                1,
                1,
                instant(base),
                instant(base.plusMinutes(5)),
                0,
                0x01,
                2,
                1,
                1,
                256_000L);
        RecordedMediaResource second = new RecordedMediaResource(
                2,
                1,
                instant(base.plusMinutes(10)),
                instant(base.plusMinutes(15)),
                0,
                0,
                2,
                1,
                1,
                512_000L);
        TerminalMediaCatalog catalog = new TerminalMediaCatalog(List.of(first, second));

        List<?> results = catalog.query(new Jt1078Command.QueryResourceList(
                1,
                bcd(base.plusMinutes(1)),
                bcd(base.plusMinutes(2)),
                0,
                0x01,
                2,
                1,
                1));

        assertEquals(1, results.size());
    }

    @Test
    void playbackTargetClipsToRequestedPlaybackWindow() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 10, 10, 0, 0);
        RecordedMediaResource resource = new RecordedMediaResource(
                1,
                1,
                instant(base),
                instant(base.plusMinutes(5)),
                0,
                0,
                2,
                1,
                1,
                256_000L);
        TerminalMediaCatalog catalog = new TerminalMediaCatalog(List.of(resource));

        PlaybackSelection selection = catalog.playbackTarget(new Jt1078Command.PlaybackRequest(
                "127.0.0.1",
                1078,
                0,
                1,
                2,
                1,
                1,
                0,
                1,
                bcd(base.plusMinutes(1)),
                bcd(base.plusMinutes(3))));

        assertNotNull(selection);
        assertEquals(instant(base.plusMinutes(1)), selection.effectiveStartTime());
        assertEquals(instant(base.plusMinutes(3)), selection.effectiveEndTime());
    }

    @Test
    void uploadTargetsReuseCatalogFiltering() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 10, 10, 0, 0);
        RecordedMediaResource first = new RecordedMediaResource(
                1,
                1,
                instant(base),
                instant(base.plusMinutes(5)),
                0,
                0,
                2,
                1,
                1,
                256_000L);
        RecordedMediaResource second = new RecordedMediaResource(
                2,
                2,
                instant(base),
                instant(base.plusMinutes(5)),
                0,
                0,
                2,
                1,
                1,
                256_000L);
        TerminalMediaCatalog catalog = new TerminalMediaCatalog(List.of(first, second));

        List<RecordedMediaResource> uploads = catalog.uploadTargets(new Jt1078Command.FileUploadCommand(
                "127.0.0.1",
                21,
                "user",
                "pass",
                "/upload",
                2,
                bcd(base.minusMinutes(1)),
                bcd(base.plusMinutes(1)),
                0,
                0,
                2,
                1,
                1,
                0));

        assertEquals(1, uploads.size());
        assertEquals(2, uploads.get(0).channel());
        assertTrue(uploads.get(0).overlaps(instant(base.minusMinutes(1)), instant(base.plusMinutes(1))));
    }

    private static Instant instant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static byte[] bcd(LocalDateTime value) {
        return new byte[] {
                encodeBcd(value.getYear() % 100),
                encodeBcd(value.getMonthValue()),
                encodeBcd(value.getDayOfMonth()),
                encodeBcd(value.getHour()),
                encodeBcd(value.getMinute()),
                encodeBcd(value.getSecond())
        };
    }

    private static byte encodeBcd(int value) {
        return (byte) (((value / 10) << 4) | (value % 10));
    }
}
