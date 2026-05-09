package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import io.netty.buffer.ByteBuf;

public class Jt1078MediaPacket {
    private final String terminalId;
    private final int channel;
    private final long sequence;
    private final SyntheticMediaSource source;
    private final int payloadBytes;

    public Jt1078MediaPacket(String terminalId, int channel, long sequence, SyntheticMediaSource source, int payloadBytes) {
        this.terminalId = terminalId;
        this.channel = channel;
        this.sequence = sequence;
        this.source = source;
        this.payloadBytes = payloadBytes;
    }

    public ByteBuf encode(ByteBuf out) {
        out.writeInt(0x30316364);
        Jt808CodecSupport.writeBcdDigits(out, terminalId, 6);
        out.writeShort((int) (sequence & 0xFFFF));
        out.writeByte(0x10);
        out.writeByte(channel);
        out.writeByte(0);
        out.writeByte(0);
        out.writeLong(System.currentTimeMillis());
        out.writeShort(payloadBytes);
        source.writePayload(out, payloadBytes, sequence);
        return out;
    }
}
