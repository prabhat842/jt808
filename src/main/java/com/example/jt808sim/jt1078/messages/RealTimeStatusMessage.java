package com.example.jt808sim.jt1078.messages;

import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.messages.AbstractJt808Message;
import io.netty.buffer.ByteBuf;

public class RealTimeStatusMessage extends AbstractJt808Message {
    private final int channel;
    private final int packetLossPercentTimes100;

    public RealTimeStatusMessage(int sequence, String terminalId, int channel, int packetLossPercentTimes100) {
        super(sequence, terminalId, true);
        this.channel = channel;
        this.packetLossPercentTimes100 = packetLossPercentTimes100;
    }

    @Override
    public int messageId() {
        return MessageIds.JT1078_REALTIME_STATUS;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(channel);
        out.writeByte(packetLossPercentTimes100);
    }
}
