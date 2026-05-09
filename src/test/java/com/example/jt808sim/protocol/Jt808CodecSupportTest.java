package com.example.jt808sim.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Jt808CodecSupportTest {
    @Test
    void writesAndReadsBcdTerminalId() {
        ByteBuf buf = Unpooled.buffer();

        Jt808CodecSupport.writeBcdDigits(buf, "00000000000000000001", 10);

        assertEquals("00000000000000000001", Jt808CodecSupport.readBcdDigits(buf, 10));
    }

    @Test
    void xorUsesHeaderAndBodyBytes() {
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[]{0x01, 0x02, 0x03, 0x04});

        assertEquals(0x04, Jt808CodecSupport.xor(buf, 0, 4));
    }
}
