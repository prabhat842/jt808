package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class TerminalGeneralResponseMessage extends AbstractJt808Message {
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_FAILURE = 1;
    public static final int RESULT_UNSUPPORTED = 3;

    private final int responseSequence;
    private final int responseMessageId;
    private final int result;

    public TerminalGeneralResponseMessage(int sequence, String terminalId, int responseSequence, int responseMessageId, int result) {
        super(sequence, terminalId, false);
        this.responseSequence = responseSequence;
        this.responseMessageId = responseMessageId;
        this.result = result;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_GENERAL_RESPONSE;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSequence);
        out.writeShort(responseMessageId);
        out.writeByte(result);
    }
}
