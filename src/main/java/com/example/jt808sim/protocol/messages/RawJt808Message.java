package com.example.jt808sim.protocol.messages;

import io.netty.buffer.ByteBuf;

public class RawJt808Message extends AbstractJt808Message {
    private final int messageId;
    private final byte[] body;

    public RawJt808Message(int messageId, int sequence, String terminalId, byte[] body) {
        super(sequence, terminalId, false);
        this.messageId = messageId;
        this.body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public int messageId() {
        return messageId;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeBytes(body);
    }
}
