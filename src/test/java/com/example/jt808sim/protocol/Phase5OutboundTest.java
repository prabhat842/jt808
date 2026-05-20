package com.example.jt808sim.protocol;

import com.example.jt808sim.fleet.Jt808MultimediaStore;
import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.messages.CameraSnapshotRespMessage;
import com.example.jt808sim.protocol.messages.EventReportMessage;
import com.example.jt808sim.protocol.messages.MultimediaDataUploadMessage;
import com.example.jt808sim.protocol.messages.MultimediaEventMessage;
import com.example.jt808sim.protocol.messages.QuestionResponseMessage;
import com.example.jt808sim.protocol.messages.StoreMediaRetrieveRespMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Encoding tests for Phase 5 outbound messages.
 */
class Phase5OutboundTest {

    private static final String TERMINAL = "00000000000000000001";
    private static final Coordinate POS   = new Coordinate(31.23, 121.47);

    // ── EventReportMessage (0x0301) ───────────────────────────────────────────

    @Test
    void eventReportEncodesSingleEventId() {
        ByteBuf buf = Unpooled.buffer();
        new EventReportMessage(1, TERMINAL, 42).encodeBody(buf);
        assertEquals(1, buf.readableBytes());
        assertEquals(42, buf.readUnsignedByte());
    }

    @Test
    void eventReportHasCorrectMessageId() {
        assertEquals(MessageIds.EVENT_REPORT, new EventReportMessage(1, TERMINAL, 1).messageId());
    }

    // ── QuestionResponseMessage (0x0302) ──────────────────────────────────────

    @Test
    void questionResponseEncoding() {
        ByteBuf buf = Unpooled.buffer();
        new QuestionResponseMessage(5, TERMINAL, 100, 2).encodeBody(buf);
        assertEquals(100, buf.readUnsignedShort()); // responseSerial
        assertEquals(2, buf.readUnsignedByte());    // answerId
        assertEquals(0, buf.readableBytes());
    }

    // ── MultimediaEventMessage (0x0800) ───────────────────────────────────────

    @Test
    void multimediaEventEncodes8Bytes() {
        ByteBuf buf = Unpooled.buffer();
        new MultimediaEventMessage(3, TERMINAL, 1_000_001L, 0, 0, 1, 1).encodeBody(buf);
        // DWORD mediaId + BYTE type + BYTE format + BYTE event + BYTE channel = 8 bytes
        assertEquals(8, buf.readableBytes());
        assertEquals(1_000_001L, buf.readUnsignedInt());
        assertEquals(0, buf.readUnsignedByte()); // mediaType=image
        assertEquals(0, buf.readUnsignedByte()); // formatCode=JPEG
        assertEquals(1, buf.readUnsignedByte()); // eventCode=timing
        assertEquals(1, buf.readUnsignedByte()); // channelId
    }

    // ── MultimediaDataUploadMessage (0x0801) ──────────────────────────────────

    @Test
    void multimediaDataUploadContains28ByteLocationBlock() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        VehicleState vs = new VehicleState();
        ByteBuf buf = Unpooled.buffer();
        new MultimediaDataUploadMessage(
                7, TERMINAL,
                1_000_002L, 0, 0, 0, 1,
                POS, 60.0, 90,
                Instant.parse("2026-05-19T08:00:00Z"), vs,
                payload
        ).encodeBody(buf);

        // Header: 4(mediaId) + 1(type) + 1(format) + 1(event) + 1(channel) = 8 bytes
        // Location: 28 bytes
        // Payload: 3 bytes
        assertEquals(8 + 28 + 3, buf.readableBytes());

        // Skip header fields
        buf.skipBytes(8);

        // Location block: alarm(4) + status(4) + lat(4) + lon(4) + alt(2) + speed(2) + heading(2) + time(6) = 28
        buf.skipBytes(4 + 4); // alarm + status
        long lat = buf.readUnsignedInt();
        long lon = buf.readUnsignedInt();
        // lat should be ~31230000 (31.23 × 1e6)
        assertTrue(lat > 31_000_000L && lat < 32_000_000L);
        // lon should be ~121470000
        assertTrue(lon > 121_000_000L && lon < 122_000_000L);
    }

    @Test
    void multimediaDataUploadHasCorrectMessageId() {
        assertEquals(MessageIds.MULTIMEDIA_DATA_UPLOAD,
                new MultimediaDataUploadMessage(1, TERMINAL, 1, 0, 0, 0, 1,
                        POS, 0, 0, Instant.now(), new VehicleState(), new byte[0]).messageId());
    }

    // ── CameraSnapshotRespMessage (0x0805) ────────────────────────────────────

    @Test
    void cameraSnapshotRespEncoding() {
        ByteBuf buf = Unpooled.buffer();
        List<Long> ids = List.of(1_000_001L, 1_000_002L, 1_000_003L);
        new CameraSnapshotRespMessage(2, TERMINAL, 10, 0, ids).encodeBody(buf);

        assertEquals(10, buf.readUnsignedShort()); // responseSerial
        assertEquals(0,  buf.readUnsignedByte());  // result=success
        assertEquals(3,  buf.readUnsignedShort()); // count
        assertEquals(1_000_001L, buf.readUnsignedInt());
        assertEquals(1_000_002L, buf.readUnsignedInt());
        assertEquals(1_000_003L, buf.readUnsignedInt());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void cameraSnapshotRespFailureHasEmptyIdList() {
        ByteBuf buf = Unpooled.buffer();
        new CameraSnapshotRespMessage(2, TERMINAL, 10, 1, List.of()).encodeBody(buf);
        buf.skipBytes(2); // serial
        assertEquals(1, buf.readUnsignedByte()); // result=failure
        assertEquals(0, buf.readUnsignedShort()); // count=0
    }

    // ── StoreMediaRetrieveRespMessage (0x0802) ────────────────────────────────

    @Test
    void storeMediaRetrieveRespEncodesAllItems() {
        Jt808MultimediaStore store = new Jt808MultimediaStore();
        long id1 = store.add(0, 0, 1, 1, POS, 30.0);
        long id2 = store.add(0, 0, 1, 2, POS, 50.0);
        List<Jt808MultimediaStore.MultimediaItem> items =
                store.query(0, 0, 0, null, null);

        VehicleState vs = new VehicleState();
        ByteBuf buf = Unpooled.buffer();
        new StoreMediaRetrieveRespMessage(3, TERMINAL, 20, items, vs).encodeBody(buf);

        assertEquals(20, buf.readUnsignedShort()); // responseSerial
        assertEquals(2, buf.readUnsignedShort());  // item count

        // Each item: DWORD(id) + BYTE(type) + BYTE(channel) + BYTE(event) + BYTE[28](location) = 35 bytes
        int remaining = buf.readableBytes();
        assertEquals(2 * 35, remaining);
    }

    @Test
    void storeMediaRetrieveRespEmptyList() {
        VehicleState vs = new VehicleState();
        ByteBuf buf = Unpooled.buffer();
        new StoreMediaRetrieveRespMessage(1, TERMINAL, 5, List.of(), vs).encodeBody(buf);
        assertEquals(5,  buf.readUnsignedShort()); // responseSerial
        assertEquals(0,  buf.readUnsignedShort()); // count=0
        assertEquals(0, buf.readableBytes());
    }
}
