package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import io.netty.buffer.ByteBuf;

public class Jt1078MediaPacket {
    private static final int HEADER_ID = 0x30316364;
    private static final int RTP_FLAGS = 0x81;
    private static final int PAYLOAD_TYPE_H264 = 98;
    private static final int DATA_TYPE_VIDEO_P_FRAME = 0x1;
    private static final int SUBPACKAGE_ATOMIC = 0x0;

    private final String terminalId;
    private final int channel;
    private final long sequence;
    private final MediaPayloadSource source;
    private final int payloadBytes;

    public Jt1078MediaPacket(String terminalId, int channel, long sequence, MediaPayloadSource source, int payloadBytes) {
        this.terminalId = terminalId;
        this.channel = channel;
        this.sequence = sequence;
        this.source = source;
        this.payloadBytes = payloadBytes;
    }

    public ByteBuf encode(ByteBuf out) {
        out.writeInt(HEADER_ID);
        out.writeByte(RTP_FLAGS);
        out.writeByte(0x80 | PAYLOAD_TYPE_H264);
        out.writeShort((int) (sequence & 0xFFFF));
        Jt808CodecSupport.writeBcdDigits(out, terminalId, 6);
        out.writeByte(channel);
        out.writeByte((DATA_TYPE_VIDEO_P_FRAME << 4) | SUBPACKAGE_ATOMIC);
        out.writeLong(sequence * 40);
        out.writeShort(0);
        out.writeShort(40);
        out.writeShort(payloadBytes);
        source.writePayload(out, payloadBytes, sequence);
        return out;
    }
}
