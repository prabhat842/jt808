package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/** 0x0003 Terminal logout — empty body (JT808-2013 §8.7). */
public class LogoutMessage extends AbstractJt808Message {
    public LogoutMessage(int sequence, String terminalId) {
        super(sequence, terminalId, true);
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_LOGOUT;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        // no body
    }
}
