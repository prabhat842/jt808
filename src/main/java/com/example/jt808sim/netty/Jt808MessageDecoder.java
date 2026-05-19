package com.example.jt808sim.netty;

import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.jt1078.Jt1078CommandDecoder;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.Jt808Header;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.ParameterSetting;
import com.example.jt808sim.protocol.RegistrationResponse;
import com.example.jt808sim.protocol.ServerAck;
import com.example.jt808sim.protocol.TerminalRegistration;
import com.example.jt808sim.protocol.TerminalGeneralResponse;
import com.example.jt808sim.protocol.TerminalLocationReport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (messageId == MessageIds.TERMINAL_GENERAL_RESPONSE && body.readableBytes() >= 5) {
            int responseSequence = body.readUnsignedShort();
            int responseMessageId = body.readUnsignedShort();
            int result = body.readUnsignedByte();
            return new TerminalGeneralResponse(responseSequence, responseMessageId, result);
        }
        if (messageId == MessageIds.REGISTER_RESPONSE && body.readableBytes() >= 3) {
            int responseSequence = body.readUnsignedShort();
            int result = body.readUnsignedByte();
            String authCode = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new RegistrationResponse(responseSequence, result, authCode);
        }
        if (messageId == MessageIds.TERMINAL_REGISTER) {
            return decodeTerminalRegistration(body);
        }
        if (messageId == MessageIds.LOCATION_REPORT) {
            TerminalLocationReport location = decodeLocationReport(body);
            if (location != null) {
                return location;
            }
        }
        if (messageId == MessageIds.TERMINAL_PARAM_SETTING) {
            return decodeParameterSetting(body);
        }
        Object jt1078Command = Jt1078CommandDecoder.decode(messageId, body);
        if (jt1078Command != null) {
            return jt1078Command;
        }
        return body.copy();
    }

    private static TerminalRegistration decodeTerminalRegistration(ByteBuf body) {
        int provinceId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        int cityId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        String manufacturerId = readTrimmedAscii(body, 11);
        String terminalModel = readTrimmedAscii(body, 30);
        String terminalIdentifier = readTrimmedAscii(body, 30);
        int plateColor = body.isReadable() ? body.readUnsignedByte() : 0;
        String plateNumber = body.isReadable() ? body.toString(body.readerIndex(), body.readableBytes(), Jt808CodecSupport.GBK).trim() : "";
        body.skipBytes(body.readableBytes());
        return new TerminalRegistration(provinceId, cityId, manufacturerId, terminalModel, terminalIdentifier, plateColor, plateNumber);
    }

    private static TerminalLocationReport decodeLocationReport(ByteBuf body) {
        if (body.readableBytes() < 28) {
            return null;
        }
        int start = body.readerIndex();
        long warnBit = body.readUnsignedInt();
        long stateBit = body.readUnsignedInt();
        double latitude = body.readUnsignedInt() / 1_000_000.0;
        double longitude = body.readUnsignedInt() / 1_000_000.0;
        int altitude = body.readUnsignedShort();
        double speed = body.readUnsignedShort() / 10.0;
        int direction = body.readUnsignedShort();
        Instant gpsTime = readBcdTimestamp(body);
        body.readerIndex(start + 28);
        return new TerminalLocationReport(warnBit, stateBit, latitude, longitude, altitude, speed, direction, gpsTime);
    }

    private static Instant readBcdTimestamp(ByteBuf body) {
        int year = 2000 + readBcdByte(body);
        int month = readBcdByte(body);
        int day = readBcdByte(body);
        int hour = readBcdByte(body);
        int minute = readBcdByte(body);
        int second = readBcdByte(body);
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeException e) {
            return Instant.EPOCH;
        }
    }

    private static int readBcdByte(ByteBuf body) {
        int value = body.readUnsignedByte();
        return ((value >>> 4) * 10) + (value & 0x0F);
    }

    private static ParameterSetting decodeParameterSetting(ByteBuf body) {
        Map<Integer, byte[]> params = new HashMap<>();
        if (!body.isReadable()) return ParameterSetting.of(params);
        int count = body.readUnsignedByte();
        for (int i = 0; i < count && body.readableBytes() >= 5; i++) {
            int paramId = (int) body.readUnsignedInt();
            int length = body.readUnsignedByte();
            if (body.readableBytes() < length) break;
            byte[] value = new byte[length];
            body.readBytes(value);
            params.put(paramId, value);
        }
        return ParameterSetting.of(params);
    }

    private static String readTrimmedAscii(ByteBuf body, int length) {
        int readable = Math.min(length, body.readableBytes());
        String value = body.toString(body.readerIndex(), readable, java.nio.charset.StandardCharsets.US_ASCII).trim();
        body.skipBytes(readable);
        return value;
    }
}
