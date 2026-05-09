package com.example.jt808sim.netty;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.Jt808Header;
import com.example.jt808sim.protocol.OutboundJt808Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class Jt808MessageEncoder extends MessageToMessageEncoder<OutboundJt808Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, OutboundJt808Message msg, List<Object> out) {
        ByteBuf body = ctx.alloc().buffer(128);
        ByteBuf packet = ctx.alloc().buffer(128);
        try {
            msg.encodeBody(body);
            packet.writeShort(msg.messageId());
            packet.writeShort(Jt808Header.bodyProperties(body.readableBytes(), true));
            packet.writeByte(Jt808Header.JT808_2019_VERSION);
            Jt808CodecSupport.writeBcdDigits(packet, msg.terminalId(), 10);
            packet.writeShort(msg.sequence());
            packet.writeBytes(body, body.readerIndex(), body.readableBytes());
            packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));
            out.add(packet.retain());
        } finally {
            body.release();
            packet.release();
        }
    }
}
