package com.example.jt808sim.jt1078;

import io.netty.buffer.ByteBuf;

public class SyntheticMediaSource {
    public void writePayload(ByteBuf out, int bytes, long sequence) {
        for (int i = 0; i < bytes; i++) {
            out.writeByte((int) ((sequence + i) & 0xFF));
        }
    }
}
