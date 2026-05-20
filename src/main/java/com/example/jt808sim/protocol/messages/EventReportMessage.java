package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/** 0x0301 Event report (Table 41, JT808-2013). Requires 0x8001 platform response. */
public class EventReportMessage extends AbstractJt808Message {
    private final int eventId;

    public EventReportMessage(int sequence, String terminalId, int eventId) {
        super(sequence, terminalId, true);
        this.eventId = eventId;
    }

    @Override public int messageId() { return MessageIds.EVENT_REPORT; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(eventId);
    }
}
