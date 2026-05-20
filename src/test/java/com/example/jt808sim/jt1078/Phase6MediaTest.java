package com.example.jt808sim.jt1078;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Phase6MediaTest {

    // ── CyclicVideoFrameSource ────────────────────────────────────────────────

    @Test
    void cyclicSourceProducesIFrameAtGopBoundary() {
        CyclicVideoFrameSource src = new CyclicVideoFrameSource(100);
        assertEquals(Jt1078FrameType.VIDEO_I, src.nextFrame(0).type());
        assertEquals(Jt1078FrameType.VIDEO_I, src.nextFrame(30).type());
        assertEquals(Jt1078FrameType.VIDEO_I, src.nextFrame(60).type());
    }

    @Test
    void cyclicSourceProducesBFrames() {
        CyclicVideoFrameSource src = new CyclicVideoFrameSource(100);
        // Within the first GOP (frames 1–29), frame at posInGop % 3 == 2 → B-frame
        boolean foundB = false;
        for (long i = 1; i < 30; i++) {
            if (src.nextFrame(i).type() == Jt1078FrameType.VIDEO_B) {
                foundB = true;
                break;
            }
        }
        assertTrue(foundB, "Expected at least one B-frame within GOP");
    }

    @Test
    void cyclicSourceTimestampAdvancesWith25Fps() {
        CyclicVideoFrameSource src = new CyclicVideoFrameSource(100);
        Jt1078Frame f0 = src.nextFrame(0);
        Jt1078Frame f1 = src.nextFrame(1);
        assertEquals(0L, f0.timestampMillis());
        assertEquals(40L, f1.timestampMillis()); // 1000ms / 25fps = 40ms
    }

    // ── G711AudioFrameSource ──────────────────────────────────────────────────

    @Test
    void g711AudioFrameIs160BytesMuLawSilence() {
        G711AudioFrameSource src = new G711AudioFrameSource();
        byte[] payload = src.nextFrame(1).payload();
        assertEquals(160, payload.length);
        for (byte b : payload) {
            assertEquals((byte) 0xFF, b, "Expected 0xFF mu-law silence");
        }
    }

    @Test
    void g711AudioFrameTypeIsAudio() {
        G711AudioFrameSource src = new G711AudioFrameSource();
        assertEquals(Jt1078FrameType.AUDIO, src.nextFrame(0).type());
    }

    @Test
    void g711AudioTimestampAdvancesBy20ms() {
        G711AudioFrameSource src = new G711AudioFrameSource();
        assertEquals(0L,  src.nextFrame(0).timestampMillis());
        assertEquals(20L, src.nextFrame(1).timestampMillis());
        assertEquals(40L, src.nextFrame(2).timestampMillis());
    }

    // ── PtzState ─────────────────────────────────────────────────────────────

    @Test
    void ptzRotationTiltUp() {
        PtzState state = new PtzState();
        int before = state.tiltTenthDegrees();
        state.applyRotation(1, 10); // up
        assertEquals(before + 10, state.tiltTenthDegrees());
    }

    @Test
    void ptzRotationPanRightWraps() {
        PtzState state = new PtzState();
        // Force pan to near maximum
        for (int i = 0; i < 36; i++) state.applyRotation(4, 100); // +3600
        assertEquals(0, state.panTenthDegrees()); // should wrap to 0
    }

    @Test
    void ptzTiltClampsAtZero() {
        PtzState state = new PtzState();
        state.applyRotation(2, 10000); // down past limit
        assertEquals(0, state.tiltTenthDegrees());
    }

    @Test
    void ptzWiperAndInfraredToggle() {
        PtzState state = new PtzState();
        assertFalse(state.wiperOn());
        assertFalse(state.infraredOn());
        state.setWiper(true);
        state.setInfrared(true);
        assertTrue(state.wiperOn());
        assertTrue(state.infraredOn());
        state.setWiper(false);
        assertFalse(state.wiperOn());
        assertTrue(state.infraredOn()); // unaffected
    }

    @Test
    void ptzFocusAndZoomApply() {
        PtzState state = new PtzState();
        int baseFocal = state.focalLength();
        int baseZoom = state.zoom();
        state.applyFocus(0);   // increase focal
        state.applyZoom(0);    // zoom in
        assertTrue(state.focalLength() > baseFocal);
        assertTrue(state.zoom() > baseZoom);
    }

    // ── Stream packet encoding correctness (Table 19, JT/T 1078-2016) ─────────

    @Test
    void audioPacketOmitsIntervalFields() {
        Jt1078Frame frame = new Jt1078Frame(Jt1078FrameType.AUDIO, 100L, false, 0, 0, new byte[20]);
        ByteBuf buf = Unpooled.buffer();
        new Jt1078MediaPacket("000000000001", 1, 1, frame, Jt1078MediaPacket.Subpackage.ATOMIC, frame.payload())
                .encode(buf);
        // Layout: header(4)+flags(2)+seq(2)+SIM(6)+channel(1)+dataType(1)+timestamp(8)+payloadLen(2)+payload(20) = 46
        // If intervals were wrongly included: 46 + 4 = 50
        assertEquals(46, buf.readableBytes());
    }

    @Test
    void passthroughPacketOmitsTimestampAndIntervals() {
        Jt1078Frame frame = new Jt1078Frame(Jt1078FrameType.PASSTHROUGH, 0L, false, 0, 0, new byte[20]);
        ByteBuf buf = Unpooled.buffer();
        new Jt1078MediaPacket("000000000001", 1, 1, frame, Jt1078MediaPacket.Subpackage.ATOMIC, frame.payload())
                .encode(buf);
        // Layout: header(4)+flags(2)+seq(2)+SIM(6)+channel(1)+dataType(1)+payloadLen(2)+payload(20) = 38
        // (no 8-byte timestamp, no 4-byte intervals)
        assertEquals(38, buf.readableBytes());
    }

    @Test
    void videoPacketRetainsFullLayout() {
        Jt1078Frame frame = new Jt1078Frame(Jt1078FrameType.VIDEO_P, 280L, false, 0, 40, new byte[20]);
        ByteBuf buf = Unpooled.buffer();
        new Jt1078MediaPacket("000000000001", 1, 1, frame, Jt1078MediaPacket.Subpackage.ATOMIC, frame.payload())
                .encode(buf);
        // header(4)+flags(2)+seq(2)+SIM(6)+ch(1)+dataType(1)+ts(8)+iInterval(2)+fInterval(2)+len(2)+payload(20) = 50
        assertEquals(50, buf.readableBytes());
    }
}
