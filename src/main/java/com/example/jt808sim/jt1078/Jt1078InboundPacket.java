package com.example.jt808sim.jt1078;

public record Jt1078InboundPacket(
        long sequence,
        String terminalId,
        int channel,
        Jt1078FrameType frameType,
        int subpackage,
        long timestampMillis,
        byte[] payload) {
}
