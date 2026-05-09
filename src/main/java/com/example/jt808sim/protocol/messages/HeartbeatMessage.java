package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class HeartbeatMessage extends AbstractJt808Message {
    public HeartbeatMessage(int sequence, String terminalId) {
        super(sequence, terminalId, true);
    }

    @Override
    public int messageId() {
        return MessageIds.HEARTBEAT;
    }

    @Override
    public void encodeBody(ByteBuf out) {
    }
}
