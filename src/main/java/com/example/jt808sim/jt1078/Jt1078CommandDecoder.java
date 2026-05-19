package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public final class Jt1078CommandDecoder {
    private Jt1078CommandDecoder() {
    }

    public static Jt1078Command decode(int messageId, ByteBuf body) {
        return switch (messageId) {
            case MessageIds.JT1078_QUERY_AV_ATTRIBUTES -> new Jt1078Command.QueryAudioVideoAttributes();
            case MessageIds.JT1078_REALTIME_REQUEST -> decodeRealTimeRequest(body);
            case MessageIds.JT1078_REALTIME_CONTROL -> decodeRealTimeControl(body);
            case MessageIds.JT1078_QUERY_RESOURCE_LIST -> decodeQueryResourceList(body);
            case MessageIds.JT1078_PLAYBACK_REQUEST -> decodePlaybackRequest(body);
            case MessageIds.JT1078_PLAYBACK_CONTROL -> decodePlaybackControl(body);
            case MessageIds.JT1078_FILE_UPLOAD_COMMAND -> decodeFileUploadCommand(body);
            case MessageIds.JT1078_FILE_UPLOAD_CONTROL -> decodeFileUploadControl(body);
            case MessageIds.JT1078_PTZ -> decodePtz(body);
            case MessageIds.JT1078_FOCUS, MessageIds.JT1078_APERTURE, MessageIds.JT1078_WIPER,
                    MessageIds.JT1078_INFRARED, MessageIds.JT1078_ZOOM -> decodeSimpleChannelControl(messageId, body);
            default -> null;
        };
    }

    private static Jt1078Command.RealTimeRequest decodeRealTimeRequest(ByteBuf body) {
        int len = readUnsignedByte(body);
        String host = readString(body, len);
        int tcpPort = readUnsignedShort(body);
        int udpPort = readUnsignedShort(body);
        int channel = readUnsignedByte(body);
        int dataType = readUnsignedByte(body);
        int streamType = readUnsignedByte(body);
        return new Jt1078Command.RealTimeRequest(host, tcpPort, udpPort, channel, dataType, streamType);
    }

    private static Jt1078Command.RealTimeControl decodeRealTimeControl(ByteBuf body) {
        return new Jt1078Command.RealTimeControl(readUnsignedByte(body), readUnsignedByte(body), readUnsignedByte(body), readUnsignedByte(body));
    }

    private static Jt1078Command.QueryResourceList decodeQueryResourceList(ByteBuf body) {
        int channel = readUnsignedByte(body);
        byte[] start = readBytes(body, 6);
        byte[] end = readBytes(body, 6);
        long alarmHigh = readUnsignedInt(body);
        long alarmLow = readUnsignedInt(body);
        int resourceType = readUnsignedByte(body);
        int streamType = readUnsignedByte(body);
        int storageType = readUnsignedByte(body);
        return new Jt1078Command.QueryResourceList(channel, start, end, alarmHigh, alarmLow, resourceType, streamType, storageType);
    }

    private static Jt1078Command.PlaybackRequest decodePlaybackRequest(ByteBuf body) {
        int len = readUnsignedByte(body);
        String host = readString(body, len);
        int tcpPort = readUnsignedShort(body);
        int udpPort = readUnsignedShort(body);
        int channel = readUnsignedByte(body);
        int audioVideoType = readUnsignedByte(body);
        int streamType = readUnsignedByte(body);
        int storageType = readUnsignedByte(body);
        int playbackMode = readUnsignedByte(body);
        int playbackSpeed = readUnsignedByte(body);
        byte[] start = readBytes(body, 6);
        byte[] end = readBytes(body, 6);
        return new Jt1078Command.PlaybackRequest(host, tcpPort, udpPort, channel, audioVideoType, streamType, storageType, playbackMode, playbackSpeed, start, end);
    }

    private static Jt1078Command.PlaybackControl decodePlaybackControl(ByteBuf body) {
        return new Jt1078Command.PlaybackControl(readUnsignedByte(body), readUnsignedByte(body), readUnsignedByte(body), readBytes(body, 6));
    }

    private static Jt1078Command.FileUploadCommand decodeFileUploadCommand(ByteBuf body) {
        int hostLength = readUnsignedByte(body);
        String host = readString(body, hostLength);
        int port = readUnsignedShort(body);
        int usernameLength = readUnsignedByte(body);
        String username = readString(body, usernameLength);
        int passwordLength = readUnsignedByte(body);
        String password = readString(body, passwordLength);
        int pathLength = readUnsignedByte(body);
        String path = readString(body, pathLength);
        int channel = readUnsignedByte(body);
        byte[] start = readBytes(body, 6);
        byte[] end = readBytes(body, 6);
        long alarmHigh = readUnsignedInt(body);
        long alarmLow = readUnsignedInt(body);
        int resourceType = readUnsignedByte(body);
        int streamType = readUnsignedByte(body);
        int storageType = readUnsignedByte(body);
        int conditions = readUnsignedByte(body);
        return new Jt1078Command.FileUploadCommand(host, port, username, password, path, channel, start, end, alarmHigh, alarmLow, resourceType, streamType, storageType, conditions);
    }

    private static Jt1078Command.FileUploadControl decodeFileUploadControl(ByteBuf body) {
        return new Jt1078Command.FileUploadControl(readUnsignedShort(body), readUnsignedByte(body));
    }

    private static Jt1078Command.PtzControl decodePtz(ByteBuf body) {
        return new Jt1078Command.PtzControl(readUnsignedByte(body), readUnsignedByte(body), readUnsignedByte(body));
    }

    private static Jt1078Command.SimpleChannelControl decodeSimpleChannelControl(int messageId, ByteBuf body) {
        return new Jt1078Command.SimpleChannelControl(messageId, readUnsignedByte(body), readUnsignedByte(body));
    }

    private static int readUnsignedByte(ByteBuf body) {
        return body.isReadable() ? body.readUnsignedByte() : 0;
    }

    private static int readUnsignedShort(ByteBuf body) {
        return body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
    }

    private static long readUnsignedInt(ByteBuf body) {
        return body.readableBytes() >= 4 ? body.readUnsignedInt() : 0;
    }

    private static String readString(ByteBuf body, int length) {
        int readable = Math.min(Math.max(length, 0), body.readableBytes());
        String value = body.toString(body.readerIndex(), readable, Jt808CodecSupport.GBK);
        body.skipBytes(readable);
        return value;
    }

    private static byte[] readBytes(ByteBuf body, int length) {
        int readable = Math.min(Math.max(length, 0), body.readableBytes());
        byte[] value = new byte[length];
        body.readBytes(value, 0, readable);
        return value;
    }
}
