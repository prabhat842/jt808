package com.example.jt808sim.jt1078.messages;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.messages.AbstractJt808Message;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

public class PassengerTrafficMessage extends AbstractJt808Message {
    private final Instant start;
    private final Instant end;
    private final int boardings;
    private final int alightings;

    public PassengerTrafficMessage(int sequence, String terminalId, Instant start, Instant end, int boardings, int alightings) {
        super(sequence, terminalId, true);
        this.start = start;
        this.end = end;
        this.boardings = boardings;
        this.alightings = alightings;
    }

    @Override
    public int messageId() {
        return MessageIds.JT1078_PASSENGER_TRAFFIC;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        Jt808CodecSupport.writeBcdTimestamp(out, start);
        Jt808CodecSupport.writeBcdTimestamp(out, end);
        out.writeShort(boardings);
        out.writeShort(alightings);
    }
}
