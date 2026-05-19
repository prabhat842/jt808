package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

public class Jt1078PacketDecoder extends ByteToMessageDecoder {
    private static final int HEADER_ID = 0x30316364;
    private static final int MIN_PACKET_BYTES = 30;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= MIN_PACKET_BYTES) {
            in.markReaderIndex();
            int headerId = in.readInt();
            if (headerId != HEADER_ID) {
                throw new CorruptedFrameException("invalid JT1078 header");
            }
            in.skipBytes(1); // RTP flags
            in.skipBytes(1); // marker + payload type
            long sequence = in.readUnsignedShort();
            String terminalId = Jt808CodecSupport.readBcdDigits(in, 6);
            int channel = in.readUnsignedByte();
            int typeAndSubpackage = in.readUnsignedByte();
            long timestamp = in.readLong();
            in.skipBytes(2); // previous I-frame interval
            in.skipBytes(2); // previous frame interval
            int payloadLength = in.readUnsignedShort();
            if (in.readableBytes() < payloadLength) {
                in.resetReaderIndex();
                return;
            }
            byte[] payload = new byte[payloadLength];
            in.readBytes(payload);
            out.add(new Jt1078InboundPacket(
                    sequence,
                    terminalId,
                    channel,
                    frameType((typeAndSubpackage >> 4) & 0x0F),
                    typeAndSubpackage & 0x0F,
                    timestamp,
                    payload));
        }
    }

    private static Jt1078FrameType frameType(int value) {
        return switch (value) {
            case 0x0 -> Jt1078FrameType.VIDEO_I;
            case 0x1 -> Jt1078FrameType.VIDEO_P;
            case 0x2 -> Jt1078FrameType.VIDEO_B;
            case 0x3 -> Jt1078FrameType.AUDIO;
            default -> Jt1078FrameType.PASSTHROUGH;
        };
    }
}
