package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import io.netty.buffer.ByteBuf;

public class Jt1078MediaPacket {
    private static final int HEADER_ID = 0x30316364;
    private static final int RTP_FLAGS = 0x81;

    private final String terminalId;
    private final int channel;
    private final long sequence;
    private final Jt1078Frame frame;
    private final Subpackage subpackage;
    private final byte[] payload;

    public Jt1078MediaPacket(String terminalId, int channel, long sequence, Jt1078Frame frame, Subpackage subpackage, byte[] payload) {
        this.terminalId = terminalId;
        this.channel = channel;
        this.sequence = sequence;
        this.frame = frame;
        this.subpackage = subpackage;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public ByteBuf encode(ByteBuf out) {
        out.writeInt(HEADER_ID);
        out.writeByte(RTP_FLAGS);
        out.writeByte(0x80 | frame.type().payloadType());
        out.writeShort((int) (sequence & 0xFFFF));
        Jt808CodecSupport.writeBcdDigits(out, terminalId, 6);
        out.writeByte(channel);
        out.writeByte((frame.type().dataTypeNibble() << 4) | subpackage.bits());
        if (frame.type().hasTimestamp()) {
            out.writeLong(frame.timestampMillis());
        }
        if (frame.type().hasIntervals()) {
            out.writeShort(frame.previousIFrameIntervalMillis());
            out.writeShort(frame.previousFrameIntervalMillis());
        }
        out.writeShort(payload.length);
        out.writeBytes(payload);
        return out;
    }

    public enum Subpackage {
        ATOMIC(0x0),
        FIRST(0x1),
        LAST(0x2),
        MIDDLE(0x3);

        private final int bits;

        Subpackage(int bits) {
            this.bits = bits;
        }

        public int bits() {
            return bits;
        }
    }
}
