package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtsFrameParserTest {
    @Test
    void splitsAdtsFramesAcrossChunkBoundaries() {
        AdtsFrameParser parser = new AdtsFrameParser();

        byte[] frame1 = new byte[] {(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0x01, 0x3F, (byte) 0xFC, 0x11, 0x22};
        byte[] frame2 = new byte[] {(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0x01, 0x3F, (byte) 0xFC, 0x33, 0x44};

        byte[] part1 = new byte[12];
        System.arraycopy(frame1, 0, part1, 0, frame1.length);
        System.arraycopy(frame2, 0, part1, frame1.length, 3);
        byte[] part2 = new byte[frame2.length - 3];
        System.arraycopy(frame2, 3, part2, 0, part2.length);

        List<byte[]> first = parser.append(part1, part1.length);
        assertEquals(1, first.size());
        assertArrayEquals(frame1, first.get(0));

        List<byte[]> second = parser.append(part2, part2.length);
        assertEquals(1, second.size());
        assertArrayEquals(frame2, second.get(0));
    }
}
