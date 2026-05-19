package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class PlatformGeneralAckMessage extends AbstractJt808Message {
    private final int responseSequence;
    private final int responseMessageId;
    private final int result;

    public PlatformGeneralAckMessage(int sequence, String terminalId, int responseSequence, int responseMessageId, int result) {
        super(sequence, terminalId, false);
        this.responseSequence = responseSequence;
        this.responseMessageId = responseMessageId;
        this.result = result;
    }

    @Override
    public int messageId() {
        return MessageIds.SERVER_ACK;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSequence);
        out.writeShort(responseMessageId);
        out.writeByte(result);
    }
}
