package com.example.jt808sim.fleet;

import com.example.jt808sim.physics.Coordinate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase5MultimediaTest {

    private static final Coordinate POSITION = new Coordinate(31.23, 121.47);

    @Test
    void addSnapshotsGeneratesUniqueIds() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        List<Long> ids = store.addSnapshots(3, 1, 0, POSITION, 60.0);

        assertEquals(3, ids.size());
        assertEquals(3, ids.stream().distinct().count());
        assertEquals(3, store.size());
    }

    @Test
    void addedItemHasCorrectFields() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        long id = store.add(0, 0, 1, 2, POSITION, 45.0);

        Jt808MultimediaStore.MultimediaItem item = store.get(id);
        assertNotNull(item);
        assertEquals(0, item.mediaType());
        assertEquals(0, item.formatCode());
        assertEquals(1, item.eventCode());
        assertEquals(2, item.channelId());
        assertEquals(POSITION.latitude(),  item.capturePosition().latitude(),  1e-9);
        assertEquals(POSITION.longitude(), item.capturePosition().longitude(), 1e-9);
        assertEquals(45.0, item.captureSpeedKph(), 1e-9);
    }

    @Test
    void queryFiltersCorrectly() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        store.add(0, 0, 1, 1, POSITION, 0); // image, ch1, event1
        store.add(1, 2, 1, 1, POSITION, 0); // audio, ch1, event1
        store.add(0, 0, 2, 2, POSITION, 0); // image, ch2, event2

        // mediaType=0 (image) only
        List<Jt808MultimediaStore.MultimediaItem> images =
                store.query(0, 0, 0, null, null);
        assertEquals(2, images.size());
        assertTrue(images.stream().allMatch(i -> i.mediaType() == 0));

        // channelId=1 only
        List<Jt808MultimediaStore.MultimediaItem> ch1 =
                store.query(0, 1, 0, null, null);
        assertEquals(1, ch1.size());
        assertEquals(1, ch1.get(0).channelId());
    }

    @Test
    void queryByTimeRange() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        Instant before = Instant.now().minus(5, ChronoUnit.HOURS);
        Instant after  = Instant.now().plus(5, ChronoUnit.HOURS);

        store.add(0, 0, 0, 1, POSITION, 0); // captured ~now

        // Should match: captureTime is between before and after
        List<Jt808MultimediaStore.MultimediaItem> found =
                store.query(0, 0, 0, before, after);
        assertEquals(1, found.size());

        // Too narrow — all in the future
        List<Jt808MultimediaStore.MultimediaItem> none =
                store.query(0, 0, 0, after, null);
        assertTrue(none.isEmpty());
    }

    @Test
    void removeDeletesItem() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        long id = store.add(0, 0, 0, 1, POSITION, 0);
        assertNotNull(store.get(id));

        store.remove(id);
        assertNull(store.get(id));
        assertEquals(0, store.size());
    }

    @Test
    void getNonExistentReturnsNull() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        assertNull(store.get(999_999_999L));
    }

    @Test
    void syntheticJpegPayloadHasSoiEoiMarkers() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        long id = store.add(0, 0, 0, 1, POSITION, 0); // mediaType=0 → image
        byte[] payload = store.get(id).syntheticPayload();

        // JPEG SOI = 0xFF 0xD8
        assertEquals((byte) 0xFF, payload[0]);
        assertEquals((byte) 0xD8, payload[1]);
        // JPEG EOI = 0xFF 0xD9
        assertEquals((byte) 0xFF, payload[payload.length - 2]);
        assertEquals((byte) 0xD9, payload[payload.length - 1]);
    }

    @Test
    void syntheticAudioPayloadIs128Bytes() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        long id = store.add(1, 3, 0, 1, POSITION, 0); // mediaType=1 → audio
        byte[] payload = store.get(id).syntheticPayload();
        assertEquals(128, payload.length);
    }
}
