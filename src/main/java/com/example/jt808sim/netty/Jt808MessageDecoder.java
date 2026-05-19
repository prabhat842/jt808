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
import com.example.jt808sim.protocol.inbound.LocationQuery;
import com.example.jt808sim.protocol.inbound.ManualAlarmConfirm;
import com.example.jt808sim.protocol.inbound.TempLocationTracking;
import com.example.jt808sim.protocol.inbound.TerminalAttributeQuery;
import com.example.jt808sim.protocol.inbound.TerminalControl;
import com.example.jt808sim.protocol.inbound.TerminalParamQueryAll;
import com.example.jt808sim.protocol.inbound.TerminalParamQuerySpec;
import com.example.jt808sim.protocol.inbound.TerminalUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
        // ── Phase 1: new platform → terminal messages ─────────────────────────
        if (messageId == MessageIds.LOCATION_QUERY) {
            return new LocationQuery();
        }
        if (messageId == MessageIds.TEMP_LOCATION_TRACKING && body.readableBytes() >= 6) {
            int interval = body.readUnsignedShort();
            long validity = body.readUnsignedInt();
            return new TempLocationTracking(interval, validity);
        }
        if (messageId == MessageIds.MANUAL_ALARM_CONFIRM && body.readableBytes() >= 6) {
            int serial = body.readUnsignedShort();
            long mask = body.readUnsignedInt();
            return new ManualAlarmConfirm(serial, mask);
        }
        if (messageId == MessageIds.TERMINAL_PARAM_QUERY_ALL) {
            return new TerminalParamQueryAll();
        }
        if (messageId == MessageIds.TERMINAL_PARAM_QUERY_SPEC && body.isReadable()) {
            int count = body.readUnsignedByte();
            List<Integer> ids = new ArrayList<>(count);
            for (int i = 0; i < count && body.readableBytes() >= 4; i++) {
                ids.add((int) body.readUnsignedInt());
            }
            return new TerminalParamQuerySpec(ids);
        }
        if (messageId == MessageIds.TERMINAL_CONTROL && body.isReadable()) {
            int command = body.readUnsignedByte();
            String params = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new TerminalControl(command, params);
        }
        if (messageId == MessageIds.TERMINAL_ATTR_QUERY) {
            return new TerminalAttributeQuery();
        }
        if (messageId == MessageIds.TERMINAL_UPDATE && body.readableBytes() >= 7) {
            int upgradeType = body.readUnsignedByte();
            byte[] mfgId = new byte[5];
            body.readBytes(mfgId);
            int versionLen = body.readUnsignedByte();
            String version = body.readableBytes() >= versionLen
                    ? body.toString(body.readerIndex(), versionLen, java.nio.charset.StandardCharsets.US_ASCII) : "";
            body.skipBytes(Math.min(versionLen, body.readableBytes()));
            byte[] data = new byte[body.readableBytes()];
            body.readBytes(data);
            return new TerminalUpdate(upgradeType, mfgId, version, data);
        }
        // ── JT1078 signaling ──────────────────────────────────────────────────
        Object jt1078Command = Jt1078CommandDecoder.decode(messageId, body);
        if (jt1078Command != null) {
            return jt1078Command;
        }
        return body.copy();
    }

    private static TerminalRegistration decodeTerminalRegistration(ByteBuf body) {
        // Table 7 (JT808-2013): province WORD + city WORD + mfgId BYTE[5] + terminalType BYTE[20] + terminalId BYTE[7] + color BYTE + plate STRING
        int provinceId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        int cityId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        String manufacturerId = readTrimmedAscii(body, 5);
        String terminalModel = readTrimmedAscii(body, 20);
        String terminalIdentifier = readTrimmedAscii(body, 7);
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
