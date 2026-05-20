package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/** 0x0302 Question response (Table 45, JT808-2013). Requires 0x8001 platform response. */
public class QuestionResponseMessage extends AbstractJt808Message {
    private final int responseSerial;
    private final int answerId;

    public QuestionResponseMessage(int sequence, String terminalId, int responseSerial, int answerId) {
        super(sequence, terminalId, true);
        this.responseSerial = responseSerial;
        this.answerId = answerId;
    }

    @Override public int messageId() { return MessageIds.QUESTION_RESPONSE; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSerial);
        out.writeByte(answerId);
    }
}
