package com.example.jt808sim.protocol;

import io.netty.buffer.ByteBuf;

public interface OutboundJt808Message {
    int messageId();

    int sequence();

    String terminalId();

    boolean expectsServerAck();

    void encodeBody(ByteBuf out);
}
