package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class RegistrationResponseMessage extends AbstractJt808Message {
    private final int responseSequence;
    private final int result;
    private final String authCode;

    public RegistrationResponseMessage(int sequence, String terminalId, int responseSequence, int result, String authCode) {
        super(sequence, terminalId, false);
        this.responseSequence = responseSequence;
        this.result = result;
        this.authCode = authCode;
    }

    @Override
    public int messageId() {
        return MessageIds.REGISTER_RESPONSE;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSequence);
        out.writeByte(result);
        if (result == 0 && authCode != null && !authCode.isBlank()) {
            out.writeBytes(authCode.getBytes(Jt808CodecSupport.GBK));
        }
    }
}
