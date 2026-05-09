package com.example.jt808sim.jt1078;

import io.netty.buffer.ByteBuf;

public interface MediaPayloadSource {
    void writePayload(ByteBuf out, int bytes, long sequence);
}
