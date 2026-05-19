package com.example.jt808sim.jt1078;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Jt1078PacketReassemblerTest {
    @Test
    void reassemblesFragmentedAudioPackets() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt1078PacketReassembler());
        channel.writeInbound(new Jt1078InboundPacket(1, "000000000001", 1, Jt1078FrameType.AUDIO, 1, 20, new byte[] {1, 2}));
        channel.writeInbound(new Jt1078InboundPacket(2, "000000000001", 1, Jt1078FrameType.AUDIO, 3, 20, new byte[] {3, 4}));
        channel.writeInbound(new Jt1078InboundPacket(3, "000000000001", 1, Jt1078FrameType.AUDIO, 2, 20, new byte[] {5}));

        Jt1078InboundPacket packet = channel.readInbound();

        assertEquals(Jt1078FrameType.AUDIO, packet.frameType());
        assertEquals(0, packet.subpackage());
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, packet.payload());
        assertNull(channel.readInbound());
    }
}
