package com.example.jt808sim.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

public class Jt808EscapeCodec extends ByteToMessageCodec<ByteBuf> {
    private static final short DELIMITER = 0x7E;
    private static final short ESCAPE = 0x7D;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        out.writeByte(DELIMITER);
        while (msg.isReadable()) {
            int value = msg.readUnsignedByte();
            if (value == 0x7D) {
                out.writeByte(0x7D).writeByte(0x01);
            } else if (value == 0x7E) {
                out.writeByte(0x7D).writeByte(0x02);
            } else {
                out.writeByte(value);
            }
        }
        out.writeByte(DELIMITER);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        ByteBuf decoded = ctx.alloc().buffer(in.readableBytes());
        try {
            while (in.isReadable()) {
                int value = in.readUnsignedByte();
                if (value == ESCAPE) {
                    if (!in.isReadable()) {
                        throw new CorruptedFrameException("truncated JT808 escape sequence");
                    }
                    int escaped = in.readUnsignedByte();
                    if (escaped == 0x01) {
                        decoded.writeByte(0x7D);
                    } else if (escaped == 0x02) {
                        decoded.writeByte(0x7E);
                    } else {
                        throw new CorruptedFrameException("invalid JT808 escape sequence: 0x7D 0x" + Integer.toHexString(escaped));
                    }
                } else {
                    decoded.writeByte(value);
                }
            }
            out.add(decoded.retain());
        } finally {
            decoded.release();
        }
    }
}
