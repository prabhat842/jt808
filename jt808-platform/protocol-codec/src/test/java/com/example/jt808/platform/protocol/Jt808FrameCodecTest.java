package com.example.jt808.platform.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class Jt808FrameCodecTest {
    private final Jt808FrameCodec codec = new Jt808FrameCodec(ZoneId.of("Asia/Shanghai"));

    @Test
    void decodesLocationReport() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1);
        body.writeInt(2);
        body.writeInt(22_250_000);
        body.writeInt(72_200_000);
        body.writeShort(35);
        body.writeShort(456);
        body.writeShort(180);
        Jt808CodecSupport.writeBcdDigits(body, "260511165000", 6);

        DecodedJt808Message message = codec.decode(frame(MessageIds.LOCATION_REPORT, "00000000000000000001", 7, body));

        assertEquals(MessageIds.LOCATION_REPORT, message.header().messageId());
        TerminalLocationReport location = assertInstanceOf(TerminalLocationReport.class, message.body());
        assertEquals(22.25, location.latitude());
        assertEquals(72.2, location.longitude());
        assertEquals(45.6, location.speedKph());
        assertEquals(180, location.direction());
    }

    @Test
    void encodesDelimitedPlatformAck() {
        byte[] frame = codec.platformGeneralAck("00000000000000000001", 2, 7, MessageIds.LOCATION_REPORT, 0);

        assertEquals(0x7E, frame[0] & 0xFF);
        assertEquals(0x7E, frame[frame.length - 1] & 0xFF);
    }

    private static byte[] frame(int messageId, String terminalId, int sequence, ByteBuf body) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeShort(messageId);
        packet.writeShort(Jt808Header.bodyProperties(body.readableBytes(), true));
        packet.writeByte(1);
        Jt808CodecSupport.writeBcdDigits(packet, terminalId, 10);
        packet.writeShort(sequence);
        packet.writeBytes(body, body.readerIndex(), body.readableBytes());
        packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));
        byte[] raw = new byte[packet.readableBytes()];
        packet.readBytes(raw);
        return raw;
    }
}
