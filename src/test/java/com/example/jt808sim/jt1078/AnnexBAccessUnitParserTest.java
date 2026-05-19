package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnexBAccessUnitParserTest {
    @Test
    void splitsAnnexBStreamIntoAccessUnitsAcrossChunkBoundaries() {
        AnnexBAccessUnitParser parser = new AnnexBAccessUnitParser();

        byte[] part1 = new byte[] {0, 0, 0, 1, 0x67, 0x11, 0x22, 0, 0};
        byte[] part2 = new byte[] {0, 1, 0x65, 0x33, 0x44, 0, 0, 1, 0x41, 0x55};

        List<byte[]> first = parser.append(part1, part1.length);
        assertEquals(0, first.size());

        List<byte[]> second = parser.append(part2, part2.length);
        assertEquals(2, second.size());
        assertArrayEquals(new byte[] {0, 0, 0, 1, 0x67, 0x11, 0x22}, second.get(0));
        assertArrayEquals(new byte[] {0, 0, 0, 1, 0x65, 0x33, 0x44}, second.get(1));

        List<byte[]> tail = parser.finish();
        assertEquals(1, tail.size());
        assertArrayEquals(new byte[] {0, 0, 1, 0x41, 0x55}, tail.get(0));
    }

    @Test
    void detectsIdrNalAsKeyFrame() {
        assertTrue(FfmpegCameraFrameSource.isKeyFrame(new byte[] {0, 0, 0, 1, 0x65, 0x01}));
        assertFalse(FfmpegCameraFrameSource.isKeyFrame(new byte[] {0, 0, 0, 1, 0x41, 0x01}));
    }
}
