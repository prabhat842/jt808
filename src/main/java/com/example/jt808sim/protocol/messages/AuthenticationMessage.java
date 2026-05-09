package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class AuthenticationMessage extends AbstractJt808Message {
    private final String authCode;

    public AuthenticationMessage(int sequence, String terminalId, String authCode) {
        super(sequence, terminalId, true);
        this.authCode = authCode == null || authCode.isBlank() ? terminalId : authCode;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_AUTH;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeBytes(authCode.getBytes(Jt808CodecSupport.GBK));
    }
}
