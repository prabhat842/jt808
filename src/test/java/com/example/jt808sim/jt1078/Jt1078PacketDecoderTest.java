package com.example.jt808sim.jt1078;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Jt1078PacketDecoderTest {
    @Test
    void decodesAudioPacket() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt1078PacketDecoder());
        byte[] payload = new byte[] {1, 2, 3, 4};
        Jt1078Frame frame = new Jt1078Frame(Jt1078FrameType.AUDIO, 20, false, 0, 0, payload);
        ByteBuf encoded = Unpooled.buffer();
        new Jt1078MediaPacket(
                "00000000000000000001",
                1,
                9,
                frame,
                Jt1078MediaPacket.Subpackage.ATOMIC,
                payload).encode(encoded);

        channel.writeInbound(encoded);
        Jt1078InboundPacket packet = channel.readInbound();

        assertEquals(9, packet.sequence());
        assertEquals("000000000001", packet.terminalId());
        assertEquals(1, packet.channel());
        assertEquals(Jt1078FrameType.AUDIO, packet.frameType());
        assertArrayEquals(payload, packet.payload());
    }
}
