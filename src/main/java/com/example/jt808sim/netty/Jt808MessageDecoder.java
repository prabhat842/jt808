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
import com.example.jt808sim.fleet.geofence.AreaAttribute;
import com.example.jt808sim.protocol.inbound.VehicleControlCommand;
import com.example.jt808sim.fleet.geofence.CircleArea;
import com.example.jt808sim.fleet.geofence.PolygonArea;
import com.example.jt808sim.fleet.geofence.RectangleArea;
import com.example.jt808sim.fleet.geofence.RouteArea;
import com.example.jt808sim.fleet.geofence.TurningPoint;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.inbound.DeleteArea;
import com.example.jt808sim.protocol.inbound.DeleteRoute;
import com.example.jt808sim.protocol.inbound.LocationQuery;
import com.example.jt808sim.protocol.inbound.ManualAlarmConfirm;
import com.example.jt808sim.protocol.inbound.SetCircleArea;
import com.example.jt808sim.protocol.inbound.SetPolygonArea;
import com.example.jt808sim.protocol.inbound.SetRectangleArea;
import com.example.jt808sim.protocol.inbound.SetRoute;
import com.example.jt808sim.protocol.inbound.TempLocationTracking;
import com.example.jt808sim.protocol.inbound.TerminalAttributeQuery;
import com.example.jt808sim.protocol.inbound.TerminalControl;
import com.example.jt808sim.protocol.inbound.TerminalParamQueryAll;
import com.example.jt808sim.protocol.inbound.TerminalParamQuerySpec;
import com.example.jt808sim.protocol.inbound.TerminalUpdate;
import com.example.jt808sim.protocol.inbound.CallbackCommand;
import com.example.jt808sim.protocol.inbound.CameraSnapshotCommand;
import com.example.jt808sim.protocol.inbound.EventSetting;
import com.example.jt808sim.protocol.inbound.InfoOnDemandMenuSetting;
import com.example.jt808sim.protocol.inbound.InfoService;
import com.example.jt808sim.protocol.inbound.MultimediaUploadAck;
import com.example.jt808sim.protocol.inbound.PhoneBookSetting;
import com.example.jt808sim.protocol.inbound.QuestionSend;
import com.example.jt808sim.protocol.inbound.SingleMediaUploadCmd;
import com.example.jt808sim.protocol.inbound.SoundRecordCmd;
import com.example.jt808sim.protocol.inbound.StoreMediaQuery;
import com.example.jt808sim.protocol.inbound.StoreMediaUploadCmd;
import com.example.jt808sim.protocol.inbound.TextInfo;
import com.example.jt808sim.protocol.inbound.TachographDataCmd;
import com.example.jt808sim.protocol.inbound.DriverIdentityAck;
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

@SuppressWarnings("DuplicatedCode")

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
        // ── Phase 4: vehicle control ──────────────────────────────────────────
        if (messageId == MessageIds.VEHICLE_CONTROL && body.isReadable()) {
            return new VehicleControlCommand(body.readUnsignedByte());
        }
        // ── Phase 3: geofence / vehicle management ────────────────────────────
        if (messageId == MessageIds.SET_CIRCLE_AREA && body.isReadable()) {
            return decodeSetCircleArea(body);
        }
        if ((messageId == MessageIds.DELETE_CIRCLE_AREA
                || messageId == MessageIds.DELETE_RECTANGLE_AREA
                || messageId == MessageIds.DELETE_POLYGON_AREA) && body.isReadable()) {
            return decodeDeleteArea(body);
        }
        if (messageId == MessageIds.SET_RECTANGLE_AREA && body.isReadable()) {
            return decodeSetRectangleArea(body);
        }
        if (messageId == MessageIds.SET_POLYGON_AREA && body.isReadable()) {
            return decodeSetPolygonArea(body);
        }
        if (messageId == MessageIds.SET_ROUTE && body.isReadable()) {
            return decodeSetRoute(body);
        }
        if (messageId == MessageIds.DELETE_ROUTE && body.isReadable()) {
            return decodeDeleteRoute(body);
        }
        // ── Phase 6: tachograph & driver identity ─────────────────────────────
        if (messageId == MessageIds.TACHOGRAPH_CMD && body.readableBytes() >= 2) {
            int commandType = body.readUnsignedShort();
            byte[] data = new byte[body.readableBytes()];
            body.readBytes(data);
            return new TachographDataCmd(commandType, data);
        }
        if (messageId == MessageIds.DRIVER_IDENTITY_ACK && body.readableBytes() >= 8) {
            int serial = body.readUnsignedShort();
            // Platform timestamp: 6-byte BCD YYMMDDHHmmss → Instant
            Instant platformTime = readBcdAreaTime(body); // reuses same BCD reader
            if (platformTime == null) platformTime = Instant.now();
            return new DriverIdentityAck(serial, platformTime);
        }
        // ── Phase 5: information protocol & multimedia ────────────────────────
        if (messageId == MessageIds.TEXT_INFO && body.readableBytes() >= 2) {
            int sign = body.readUnsignedByte();
            String text = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new TextInfo(sign, text);
        }
        if (messageId == MessageIds.EVENT_SETTING && body.isReadable()) {
            return decodeEventSetting(body);
        }
        if (messageId == MessageIds.QUESTION_SEND && body.isReadable()) {
            return decodeQuestionSend(body);
        }
        if (messageId == MessageIds.INFO_ON_DEMAND_MENU && body.isReadable()) {
            return decodeInfoOnDemandMenu(body);
        }
        if (messageId == MessageIds.INFO_SERVICE && body.readableBytes() >= 2) {
            int infoType = body.readUnsignedByte();
            String content = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new InfoService(infoType, content);
        }
        if (messageId == MessageIds.CALLBACK && body.isReadable()) {
            int sign = body.readUnsignedByte();
            String phone = body.isReadable() ? body.toString(Jt808CodecSupport.GBK) : "";
            return new CallbackCommand(sign, phone);
        }
        if (messageId == MessageIds.PHONE_BOOK_SETTING && body.isReadable()) {
            return decodePhoneBookSetting(body);
        }
        if (messageId == MessageIds.MULTIMEDIA_UPLOAD_ACK && body.readableBytes() >= 5) {
            long mediaId = body.readUnsignedInt();
            int packetCount = body.readUnsignedByte();
            List<Integer> resend = new ArrayList<>(packetCount);
            for (int i = 0; i < packetCount && body.readableBytes() >= 2; i++) {
                resend.add(body.readUnsignedShort());
            }
            return new MultimediaUploadAck(mediaId, resend);
        }
        if (messageId == MessageIds.CAMERA_SNAPSHOT_CMD && body.readableBytes() >= 11) {
            int channelId       = body.readUnsignedByte();
            int takenCommand    = body.readUnsignedShort();
            int intervalSeconds = body.readUnsignedShort();
            int savingSign      = body.readUnsignedByte();
            int resolution      = body.readUnsignedByte();
            int quality         = body.readUnsignedByte();
            int brightness      = body.readUnsignedByte();
            int contrast        = body.readUnsignedByte();
            int saturation      = body.readUnsignedByte();
            int chroma          = body.readUnsignedByte();
            return new CameraSnapshotCommand(channelId, takenCommand, intervalSeconds,
                    savingSign, resolution, quality, brightness, contrast, saturation, chroma);
        }
        if (messageId == MessageIds.STORE_MEDIA_QUERY && body.readableBytes() >= 15) {
            int mediaType = body.readUnsignedByte();
            int channelId = body.readUnsignedByte();
            int eventCode = body.readUnsignedByte();
            Instant startTime = readBcdAreaTime(body);
            Instant endTime   = readBcdAreaTime(body);
            return new StoreMediaQuery(mediaType, channelId, eventCode, startTime, endTime);
        }
        if (messageId == MessageIds.STORE_MEDIA_UPLOAD_CMD && body.readableBytes() >= 16) {
            int mediaType          = body.readUnsignedByte();
            int channelId          = body.readUnsignedByte();
            int eventCode          = body.readUnsignedByte();
            Instant startTime      = readBcdAreaTime(body);
            Instant endTime        = readBcdAreaTime(body);
            int deleteAfterUpload  = body.isReadable() ? body.readUnsignedByte() : 0;
            return new StoreMediaUploadCmd(mediaType, channelId, eventCode,
                    startTime, endTime, deleteAfterUpload);
        }
        if (messageId == MessageIds.SOUND_RECORD_CMD && body.readableBytes() >= 4) {
            int command       = body.readUnsignedByte();
            int recordSeconds = body.readUnsignedShort();
            int storeSign     = body.readUnsignedByte();
            int samplingRate  = body.isReadable() ? body.readUnsignedByte() : 0;
            return new SoundRecordCmd(command, recordSeconds, storeSign, samplingRate);
        }
        if (messageId == MessageIds.SINGLE_MEDIA_UPLOAD_CMD && body.readableBytes() >= 5) {
            long mediaId  = body.readUnsignedInt();
            int deleteSign = body.isReadable() ? body.readUnsignedByte() : 0;
            return new SingleMediaUploadCmd(mediaId, deleteSign);
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

    // ── Geofence message decoders ─────────────────────────────────────────────

    private static SetCircleArea decodeSetCircleArea(ByteBuf body) {
        int settingAttr = body.readUnsignedByte();
        int count = body.readUnsignedByte();
        List<CircleArea> areas = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CircleArea area = decodeCircleAreaItem(body);
            if (area != null) areas.add(area);
        }
        return new SetCircleArea(settingAttr, areas);
    }

    private static CircleArea decodeCircleAreaItem(ByteBuf body) {
        // minimum: ID(4) + attr(2) + lat(4) + lon(4) + radius(4) = 18 bytes
        if (body.readableBytes() < 18) return null;
        long areaId = body.readUnsignedInt();
        AreaAttribute attr = new AreaAttribute(body.readUnsignedShort());
        double lat = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.southLatitude());
        double lon = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.westLongitude());
        long radius = body.readUnsignedInt();
        Instant startTime = null, endTime = null;
        if (attr.hasTimeWindow() && body.readableBytes() >= 12) {
            startTime = readBcdAreaTime(body);
            endTime   = readBcdAreaTime(body);
        }
        int maxSpeed = 0, overspeedSec = 0;
        if (attr.hasSpeedLimit() && body.readableBytes() >= 3) {
            maxSpeed    = body.readUnsignedShort();
            overspeedSec = body.readUnsignedByte();
        }
        return new CircleArea(areaId, attr, lat, lon, radius, startTime, endTime, maxSpeed, overspeedSec);
    }

    private static DeleteArea decodeDeleteArea(ByteBuf body) {
        if (!body.isReadable()) return new DeleteArea(List.of());
        int count = body.readUnsignedByte();
        if (count == 0) return new DeleteArea(List.of()); // delete all
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count && body.readableBytes() >= 4; i++) {
            ids.add(body.readUnsignedInt());
        }
        return new DeleteArea(ids);
    }

    private static SetRectangleArea decodeSetRectangleArea(ByteBuf body) {
        int settingAttr = body.readUnsignedByte();
        int count = body.readUnsignedByte();
        List<RectangleArea> areas = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RectangleArea area = decodeRectangleAreaItem(body);
            if (area != null) areas.add(area);
        }
        return new SetRectangleArea(settingAttr, areas);
    }

    private static RectangleArea decodeRectangleAreaItem(ByteBuf body) {
        // minimum: ID(4) + attr(2) + 4 corners × 4 = 22 bytes
        if (body.readableBytes() < 22) return null;
        long areaId = body.readUnsignedInt();
        AreaAttribute attr = new AreaAttribute(body.readUnsignedShort());
        double topLeftLat     = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.southLatitude());
        double topLeftLon     = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.westLongitude());
        double bottomRightLat = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.southLatitude());
        double bottomRightLon = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.westLongitude());
        Instant startTime = null, endTime = null;
        if (attr.hasTimeWindow() && body.readableBytes() >= 12) {
            startTime = readBcdAreaTime(body);
            endTime   = readBcdAreaTime(body);
        }
        int maxSpeed = 0, overspeedSec = 0;
        if (attr.hasSpeedLimit() && body.readableBytes() >= 3) {
            maxSpeed     = body.readUnsignedShort();
            overspeedSec = body.readUnsignedByte();
        }
        return new RectangleArea(areaId, attr, topLeftLat, topLeftLon, bottomRightLat, bottomRightLon,
                startTime, endTime, maxSpeed, overspeedSec);
    }

    private static SetPolygonArea decodeSetPolygonArea(ByteBuf body) {
        // minimum: settingAttr(1) + ID(4) + attr(2) + vertexCount(2) = 9 bytes
        if (body.readableBytes() < 9) return null;
        int settingAttr = body.readUnsignedByte();
        long areaId = body.readUnsignedInt();
        AreaAttribute attr = new AreaAttribute(body.readUnsignedShort());
        Instant startTime = null, endTime = null;
        if (attr.hasTimeWindow() && body.readableBytes() >= 12) {
            startTime = readBcdAreaTime(body);
            endTime   = readBcdAreaTime(body);
        }
        int maxSpeed = 0, overspeedSec = 0;
        if (attr.hasSpeedLimit() && body.readableBytes() >= 3) {
            maxSpeed     = body.readUnsignedShort();
            overspeedSec = body.readUnsignedByte();
        }
        int vertexCount = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        List<Coordinate> vertices = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount && body.readableBytes() >= 8; i++) {
            double vLat = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.southLatitude());
            double vLon = applyHemisphere(body.readUnsignedInt() / 1_000_000.0, attr.westLongitude());
            vertices.add(new Coordinate(vLat, vLon));
        }
        return new SetPolygonArea(settingAttr, new PolygonArea(areaId, attr, startTime, endTime,
                maxSpeed, overspeedSec, vertices));
    }

    private static SetRoute decodeSetRoute(ByteBuf body) {
        // minimum: routeID(4) + routeAttr(2) + turningCount(2) = 8 bytes
        if (body.readableBytes() < 8) return null;
        long routeId = body.readUnsignedInt();
        int routeAttr = body.readUnsignedShort();
        Instant startTime = null, endTime = null;
        if ((routeAttr & 0x0001) != 0 && body.readableBytes() >= 12) {
            startTime = readBcdAreaTime(body);
            endTime   = readBcdAreaTime(body);
        }
        int turningCount = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        List<TurningPoint> points = new ArrayList<>(turningCount);
        for (int i = 0; i < turningCount; i++) {
            TurningPoint pt = decodeTurningPoint(body);
            if (pt != null) points.add(pt);
        }
        return new SetRoute(new RouteArea(routeId, routeAttr, startTime, endTime, points));
    }

    private static TurningPoint decodeTurningPoint(ByteBuf body) {
        // minimum: pointID(4) + routeID(4) + lat(4) + lon(4) + width(1) + routeAttr(1) = 18 bytes
        if (body.readableBytes() < 18) return null;
        long pointId = body.readUnsignedInt();
        body.skipBytes(4); // route ID (repeated, matches parent)
        // hemisphere in per-point route attribute bits 2/3
        int ptAttr = 0; // read below after lat/lon
        double lat = body.readUnsignedInt() / 1_000_000.0;
        double lon = body.readUnsignedInt() / 1_000_000.0;
        int width  = body.readUnsignedByte();
        ptAttr     = body.readUnsignedByte();
        if ((ptAttr & 0x04) != 0) lat = -lat; // south latitude
        if ((ptAttr & 0x08) != 0) lon = -lon; // west longitude
        int tooLong = 0, notEnough = 0;
        if ((ptAttr & 0x01) != 0 && body.readableBytes() >= 4) { // has time thresholds
            tooLong    = body.readUnsignedShort();
            notEnough  = body.readUnsignedShort();
        }
        int ptMaxSpeed = 0, ptSpeedDuration = 0;
        if ((ptAttr & 0x02) != 0 && body.readableBytes() >= 3) { // has speed limit
            ptMaxSpeed      = body.readUnsignedShort();
            ptSpeedDuration = body.readUnsignedByte();
        }
        return new TurningPoint(pointId, lat, lon, width, tooLong, notEnough, ptMaxSpeed, ptSpeedDuration);
    }

    private static DeleteRoute decodeDeleteRoute(ByteBuf body) {
        if (!body.isReadable()) return new DeleteRoute(List.of());
        int count = body.readUnsignedByte();
        if (count == 0) return new DeleteRoute(List.of());
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count && body.readableBytes() >= 4; i++) {
            ids.add(body.readUnsignedInt());
        }
        return new DeleteRoute(ids);
    }

    // Reads a 6-byte BCD timestamp (YY-MM-DD-hh-mm-ss) as GMT+8 Instant.
    // Returns null when the bytes are all-zero (no time constraint).
    private static Instant readBcdAreaTime(ByteBuf body) {
        int year  = 2000 + readBcdByte(body);
        int month = readBcdByte(body);
        int day   = readBcdByte(body);
        int hour  = readBcdByte(body);
        int min   = readBcdByte(body);
        int sec   = readBcdByte(body);
        if (month == 0 || day == 0) return null; // all-zero sentinel
        try {
            return LocalDateTime.of(year, month, day, hour, min, sec)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        } catch (DateTimeException e) {
            return null;
        }
    }

    // Negates value when hemisphere flag is true (south lat / west lon)
    private static double applyHemisphere(double value, boolean negate) {
        return negate ? -value : value;
    }

    // ── Phase 5 helper decoders ───────────────────────────────────────────────

    private static EventSetting decodeEventSetting(ByteBuf body) {
        int settingType = body.readUnsignedByte();
        int count = body.isReadable() ? body.readUnsignedByte() : 0;
        List<EventSetting.EventItem> items = new ArrayList<>(count);
        for (int i = 0; i < count && body.isReadable(); i++) {
            int eventId = body.readUnsignedByte();
            int len = body.isReadable() ? body.readUnsignedByte() : 0;
            String content = body.readableBytes() >= len
                    ? body.toString(body.readerIndex(), len, Jt808CodecSupport.GBK) : "";
            body.skipBytes(Math.min(len, body.readableBytes()));
            items.add(new EventSetting.EventItem(eventId, content));
        }
        return new EventSetting(settingType, items);
    }

    private static QuestionSend decodeQuestionSend(ByteBuf body) {
        int sign = body.readUnsignedByte();
        int questionLen = body.isReadable() ? body.readUnsignedByte() : 0;
        String question = body.readableBytes() >= questionLen
                ? body.toString(body.readerIndex(), questionLen, Jt808CodecSupport.GBK) : "";
        body.skipBytes(Math.min(questionLen, body.readableBytes()));
        int answerCount = body.isReadable() ? body.readUnsignedByte() : 0;
        List<QuestionSend.AnswerItem> answers = new ArrayList<>(answerCount);
        for (int i = 0; i < answerCount && body.isReadable(); i++) {
            int answerId = body.readUnsignedByte();
            int len = body.isReadable() ? body.readUnsignedByte() : 0;
            String content = body.readableBytes() >= len
                    ? body.toString(body.readerIndex(), len, Jt808CodecSupport.GBK) : "";
            body.skipBytes(Math.min(len, body.readableBytes()));
            answers.add(new QuestionSend.AnswerItem(answerId, content));
        }
        return new QuestionSend(sign, question, answers);
    }

    private static InfoOnDemandMenuSetting decodeInfoOnDemandMenu(ByteBuf body) {
        int settingType = body.readUnsignedByte();
        int count = body.isReadable() ? body.readUnsignedByte() : 0;
        List<InfoOnDemandMenuSetting.InfoMenuItem> items = new ArrayList<>(count);
        for (int i = 0; i < count && body.isReadable(); i++) {
            int infoType = body.readUnsignedByte();
            int len = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
            String name = body.readableBytes() >= len
                    ? body.toString(body.readerIndex(), len, Jt808CodecSupport.GBK) : "";
            body.skipBytes(Math.min(len, body.readableBytes()));
            items.add(new InfoOnDemandMenuSetting.InfoMenuItem(infoType, name));
        }
        return new InfoOnDemandMenuSetting(settingType, items);
    }

    private static PhoneBookSetting decodePhoneBookSetting(ByteBuf body) {
        int settingType = body.readUnsignedByte();
        int count = body.isReadable() ? body.readUnsignedByte() : 0;
        List<PhoneBookSetting.ContactItem> contacts = new ArrayList<>(count);
        for (int i = 0; i < count && body.isReadable(); i++) {
            int sign = body.readUnsignedByte();
            int phoneLen = body.isReadable() ? body.readUnsignedByte() : 0;
            String phone = body.readableBytes() >= phoneLen
                    ? body.toString(body.readerIndex(), phoneLen, Jt808CodecSupport.GBK) : "";
            body.skipBytes(Math.min(phoneLen, body.readableBytes()));
            int nameLen = body.isReadable() ? body.readUnsignedByte() : 0;
            String name = body.readableBytes() >= nameLen
                    ? body.toString(body.readerIndex(), nameLen, Jt808CodecSupport.GBK) : "";
            body.skipBytes(Math.min(nameLen, body.readableBytes()));
            contacts.add(new PhoneBookSetting.ContactItem(sign, phone, name));
        }
        return new PhoneBookSetting(settingType, contacts);
    }
}
