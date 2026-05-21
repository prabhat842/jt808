package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnexBAccessUnitParserTest {

    /**
     * Stream: SPS [type 7] → IDR [type 5] → P-frame [type 1]
     *
     * Correct H.264 access unit grouping:
     *   Access unit 1: SPS + IDR  (parameter sets bundled with their coded slice)
     *   Access unit 2: P-frame
     *
     * The SPS and IDR are flushed together when the P-frame arrival signals
     * that the IDR picture is complete.
     */
    @Test
    void groupsParameterSetsWithFollowingCodedSlice() {
        AnnexBAccessUnitParser parser = new AnnexBAccessUnitParser();

        // SPS (type 7) split across two chunks
        byte[] part1 = new byte[]{0, 0, 0, 1, 0x67, 0x11, 0x22, 0, 0};
        byte[] part2 = new byte[]{0, 1, 0x65, (byte) 0x80, 0x44,   // → 00 00 01 65 ... = IDR, first slice
                                   0, 0, 1, 0x41, (byte) 0x80};     // → 00 00 01 41 ... = P-frame, first slice

        List<byte[]> first = parser.append(part1, part1.length);
        assertEquals(0, first.size(), "SPS alone should not emit yet");

        // P-frame arrival flushes [SPS + IDR] as one access unit
        List<byte[]> second = parser.append(part2, part2.length);
        assertEquals(1, second.size(), "SPS+IDR emitted as single access unit");
        assertArrayEquals(
            new byte[]{0, 0, 0, 1, 0x67, 0x11, 0x22,   // SPS
                       0, 0, 0, 1, 0x65, (byte) 0x80, 0x44},   // IDR
            second.get(0),
            "Access unit 1 must be SPS + IDR together");

        // P-frame is held until finish() or next coded slice
        List<byte[]> tail = parser.finish();
        assertEquals(1, tail.size());
        assertArrayEquals(new byte[]{0, 0, 1, 0x41, (byte) 0x80}, tail.get(0),
            "P-frame flushed by finish()");
    }

    @Test
    void consecutivePFramesEachFlushPrevious() {
        AnnexBAccessUnitParser parser = new AnnexBAccessUnitParser();

        // Two P-frames back-to-back
        byte[] data = new byte[]{
            0, 0, 0, 1, 0x41, (byte) 0x80, 0x11,   // P1
            0, 0, 0, 1, 0x41, (byte) 0x80, 0x22,   // P2
            0, 0, 0, 1, 0x41, (byte) 0x80, 0x33    // P3
        };
        List<byte[]> emitted = parser.append(data, data.length);
        // P1 flushed by P2's arrival, P2 flushed by P3's arrival
        assertEquals(2, emitted.size());
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x41, (byte) 0x80, 0x11}, emitted.get(0));
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x41, (byte) 0x80, 0x22}, emitted.get(1));

        List<byte[]> tail = parser.finish();
        assertEquals(1, tail.size());
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x41, (byte) 0x80, 0x33}, tail.get(0));
    }

    @Test
    void keepsMultipleSlicesOfSamePictureTogether() {
        AnnexBAccessUnitParser parser = new AnnexBAccessUnitParser();

        byte[] data = new byte[]{
            0, 0, 0, 1, 0x67, 0x11,             // SPS
            0, 0, 0, 1, 0x68, 0x22,             // PPS
            0, 0, 0, 1, 0x65, (byte) 0x80, 1,   // IDR first slice: first_mb_in_slice = 0
            0, 0, 0, 1, 0x65, 0x40, 2,          // IDR second slice: first_mb_in_slice = 1
            0, 0, 0, 1, 0x41, (byte) 0x80, 3    // next P-frame first slice
        };

        List<byte[]> emitted = parser.append(data, data.length);
        assertEquals(1, emitted.size());
        assertArrayEquals(new byte[]{
            0, 0, 0, 1, 0x67, 0x11,
            0, 0, 0, 1, 0x68, 0x22,
            0, 0, 0, 1, 0x65, (byte) 0x80, 1,
            0, 0, 0, 1, 0x65, 0x40, 2
        }, emitted.get(0));

        List<byte[]> tail = parser.finish();
        assertEquals(1, tail.size());
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x41, (byte) 0x80, 3}, tail.get(0));
    }

    @Test
    void detectsIdrNalAsKeyFrame() {
        assertTrue(FfmpegCameraFrameSource.isKeyFrame(new byte[]{0, 0, 0, 1, 0x65, 0x01}));
        assertFalse(FfmpegCameraFrameSource.isKeyFrame(new byte[]{0, 0, 0, 1, 0x41, 0x01}));
    }
}
