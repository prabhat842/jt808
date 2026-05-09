package com.example.jt808sim.netty;

import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.Jt808Header;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.RegistrationResponse;
import com.example.jt808sim.protocol.ServerAck;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

public class Jt808MessageDecoder extends ByteToMessageDecoder {
    private final MetricsRegistry metrics;

    public Jt808MessageDecoder(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }
        if (in.readableBytes() < 6) {
            in.skipBytes(in.readableBytes());
            throw new CorruptedFrameException("JT808 frame too short");
        }
        int checksumIndex = in.writerIndex() - 1;
        int expected = in.getUnsignedByte(checksumIndex);
        int actual = Jt808CodecSupport.xor(in, in.readerIndex(), checksumIndex);
        if (expected != actual) {
            metrics.invalidChecksum().increment();
            in.skipBytes(in.readableBytes());
            throw new CorruptedFrameException("JT808 checksum mismatch");
        }

        int messageId = in.readUnsignedShort();
        int props = in.readUnsignedShort();
        boolean versioned = (props & Jt808Header.VERSION_FLAG) != 0;
        boolean fragmented = (props & Jt808Header.FRAGMENT_FLAG) != 0;
        int bodyLength = props & Jt808Header.BODY_LENGTH_MASK;
        int protocolVersion = versioned ? in.readUnsignedByte() : 0;
        String terminalId = versioned ? Jt808CodecSupport.readBcdDigits(in, 10) : Jt808CodecSupport.readBcdDigits(in, 6);
        int sequence = in.readUnsignedShort();
        if (fragmented) {
            in.skipBytes(4);
        }

        int bodyReadable = Math.min(bodyLength, checksumIndex - in.readerIndex());
        ByteBuf body = in.readSlice(bodyReadable);
        in.readerIndex(checksumIndex + 1);

        Object decodedBody = decodeBody(messageId, body);
        Jt808Header header = new Jt808Header(messageId, props, protocolVersion, terminalId, sequence, versioned, fragmented, bodyLength);
        metrics.inboundMessages().increment();
        out.add(new Jt808Message(header, decodedBody));
    }

    private Object decodeBody(int messageId, ByteBuf body) {
        if (messageId == MessageIds.SERVER_ACK && body.readableBytes() >= 5) {
            int responseSequence = body.readUnsignedShort();
            int responseMessageId = body.readUnsignedShort();
            int result = body.readUnsignedByte();
            return new ServerAck(responseSequence, responseMessageId, result);
        }
        if (messageId == MessageIds.REGISTER_RESPONSE && body.readableBytes() >= 3) {
            int responseSequence = body.readUnsignedShort();
            int result = body.readUnsignedByte();
            String authCode = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new RegistrationResponse(responseSequence, result, authCode);
        }
        return body.copy();
    }
}
