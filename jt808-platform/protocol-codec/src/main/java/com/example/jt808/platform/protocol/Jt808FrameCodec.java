package com.example.jt808.platform.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

public class Jt808FrameCodec {
    private final ZoneId protocolZone;

    public Jt808FrameCodec(ZoneId protocolZone) {
        this.protocolZone = protocolZone;
    }

    public DecodedJt808Message decode(byte[] frameBody) {
        ByteBuf in = Unpooled.wrappedBuffer(unescape(frameBody));
        if (in.readableBytes() < 6) {
            throw new IllegalArgumentException("JT808 frame too short");
        }
        int checksumIndex = in.writerIndex() - 1;
        int expected = in.getUnsignedByte(checksumIndex);
        int actual = Jt808CodecSupport.xor(in, in.readerIndex(), checksumIndex);
        if (expected != actual) {
            throw new IllegalArgumentException("JT808 checksum mismatch");
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
            in.skipBytes(Math.min(4, in.readableBytes()));
        }

        int bodyReadable = Math.min(bodyLength, checksumIndex - in.readerIndex());
        ByteBuf body = in.readSlice(bodyReadable);
        Jt808Header header = new Jt808Header(messageId, props, protocolVersion, terminalId, sequence, versioned, fragmented, bodyLength);
        return new DecodedJt808Message(header, decodeBody(messageId, body));
    }

    public byte[] platformGeneralAck(String terminalId, int sequence, int responseSequence, int responseMessageId, int result) {
        ByteBuf body = Unpooled.buffer(5);
        body.writeShort(responseSequence);
        body.writeShort(responseMessageId);
        body.writeByte(result);
        return encode(MessageIds.PLATFORM_GENERAL_ACK, terminalId, sequence, true, body);
    }

    public byte[] registrationResponse(String terminalId, int sequence, int responseSequence, int result, String authCode) {
        ByteBuf body = Unpooled.buffer();
        body.writeShort(responseSequence);
        body.writeByte(result);
        body.writeBytes((authCode == null ? "" : authCode).getBytes(Jt808CodecSupport.GBK));
        return encode(MessageIds.REGISTER_RESPONSE, terminalId, sequence, true, body);
    }

    public ParsedCommand parseHeaderBody(byte[] headerBody) {
        ByteBuf in = Unpooled.wrappedBuffer(headerBody);
        if (in.readableBytes() < 16) {
            throw new IllegalArgumentException("JT808 command content too short");
        }
        int messageId = in.readUnsignedShort();
        int props = in.readUnsignedShort();
        boolean versioned = (props & Jt808Header.VERSION_FLAG) != 0;
        boolean fragmented = (props & Jt808Header.FRAGMENT_FLAG) != 0;
        int protocolVersion = versioned ? in.readUnsignedByte() : 0;
        String terminalId = versioned ? Jt808CodecSupport.readBcdDigits(in, 10) : Jt808CodecSupport.readBcdDigits(in, 6);
        int sequenceOffset = in.readerIndex();
        int originalSequence = in.readUnsignedShort();
        if (fragmented) {
            throw new IllegalArgumentException("fragmented platform command content is not supported yet");
        }
        byte[] body = new byte[in.readableBytes()];
        in.readBytes(body);
        return new ParsedCommand(messageId, props, protocolVersion, terminalId, originalSequence, sequenceOffset, body);
    }

    public byte[] platformCommand(ParsedCommand command, int sequence) {
        ByteBuf body = Unpooled.wrappedBuffer(command.body());
        boolean versioned = (command.props() & Jt808Header.VERSION_FLAG) != 0 || command.protocolVersion() > 0;
        return encode(command.messageId(), command.terminalId(), sequence, versioned, body);
    }

    private byte[] encode(int messageId, String terminalId, int sequence, boolean versioned, ByteBuf body) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeShort(messageId);
        packet.writeShort(Jt808Header.bodyProperties(body.readableBytes(), versioned));
        if (versioned) {
            packet.writeByte(1);
            Jt808CodecSupport.writeBcdDigits(packet, terminalId, 10);
        } else {
            Jt808CodecSupport.writeBcdDigits(packet, terminalId, 6);
        }
        packet.writeShort(sequence);
        packet.writeBytes(body, body.readerIndex(), body.readableBytes());
        packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));
        return delimit(escape(packet.array(), packet.arrayOffset() + packet.readerIndex(), packet.readableBytes()));
    }

    private Object decodeBody(int messageId, ByteBuf body) {
        if (messageId == MessageIds.TERMINAL_REGISTER) {
            return decodeRegistration(body);
        }
        if (messageId == MessageIds.TERMINAL_AUTH) {
            String token = body.isReadable() ? body.toString(body.readerIndex(), body.readableBytes(), Jt808CodecSupport.GBK).trim() : "";
            return new AuthenticationBody(token);
        }
        if (messageId == MessageIds.TERMINAL_GENERAL_RESPONSE && body.readableBytes() >= 5) {
            return new TerminalGeneralResponse(body.readUnsignedShort(), body.readUnsignedShort(), body.readUnsignedByte());
        }
        if (messageId == MessageIds.LOCATION_REPORT) {
            TerminalLocationReport location = decodeLocation(body);
            if (location != null) {
                return location;
            }
        }
        byte[] raw = new byte[body.readableBytes()];
        body.readBytes(raw);
        return new RawBody(raw);
    }

    private TerminalRegistration decodeRegistration(ByteBuf body) {
        int provinceId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        int cityId = body.readableBytes() >= 2 ? body.readUnsignedShort() : 0;
        String manufacturerId = Jt808CodecSupport.readTrimmedAscii(body, 11);
        String terminalModel = Jt808CodecSupport.readTrimmedAscii(body, 30);
        String terminalIdentifier = Jt808CodecSupport.readTrimmedAscii(body, 30);
        int plateColor = body.isReadable() ? body.readUnsignedByte() : 0;
        String plateNumber = body.isReadable() ? body.toString(body.readerIndex(), body.readableBytes(), Jt808CodecSupport.GBK).trim() : "";
        return new TerminalRegistration(provinceId, cityId, manufacturerId, terminalModel, terminalIdentifier, plateColor, plateNumber);
    }

    private TerminalLocationReport decodeLocation(ByteBuf body) {
        if (body.readableBytes() < 28) {
            return null;
        }
        long warnBit   = body.readUnsignedInt();
        long stateBit  = body.readUnsignedInt();
        double latitude  = body.readUnsignedInt() / 1_000_000.0;
        double longitude = body.readUnsignedInt() / 1_000_000.0;
        int altitude   = body.readUnsignedShort();
        double speed   = body.readUnsignedShort() / 10.0;
        int direction  = body.readUnsignedShort();
        Instant gpsTime = Jt808CodecSupport.readBcdTimestamp(body, protocolZone);

        // Additional info items (Table 26/27) — TLV: ID(1) + len(1) + value
        long mileageTenthKm  = -1;
        int  fuelTenthLiters = -1;
        int  signalStrength  = 0;
        int  satelliteCount  = 0;

        while (body.readableBytes() >= 2) {
            int infoId  = body.readUnsignedByte();
            int infoLen = body.readUnsignedByte();
            if (body.readableBytes() < infoLen) break;
            int endIdx = body.readerIndex() + infoLen;
            switch (infoId) {
                case 0x01 -> { if (infoLen >= 4) mileageTenthKm  = body.readUnsignedInt(); }
                case 0x02 -> { if (infoLen >= 2) fuelTenthLiters = body.readUnsignedShort(); }
                case 0x30 -> { if (infoLen >= 1) signalStrength   = body.readUnsignedByte(); }
                case 0x31 -> { if (infoLen >= 1) satelliteCount   = body.readUnsignedByte(); }
                default   -> { /* skip unknown additional info items */ }
            }
            body.readerIndex(endIdx); // advance past any unread bytes of this item
        }

        return new TerminalLocationReport(warnBit, stateBit, latitude, longitude,
                altitude, speed, direction, gpsTime,
                mileageTenthKm, fuelTenthLiters, signalStrength, satelliteCount);
    }

    private static byte[] unescape(byte[] frameBody) {
        byte[] out = new byte[frameBody.length];
        int write = 0;
        for (int i = 0; i < frameBody.length; i++) {
            int value = frameBody[i] & 0xFF;
            if (value == 0x7D) {
                if (++i >= frameBody.length) {
                    throw new IllegalArgumentException("truncated JT808 escape sequence");
                }
                int escaped = frameBody[i] & 0xFF;
                if (escaped == 0x01) {
                    out[write++] = 0x7D;
                } else if (escaped == 0x02) {
                    out[write++] = 0x7E;
                } else {
                    throw new IllegalArgumentException("invalid JT808 escape sequence");
                }
            } else {
                out[write++] = frameBody[i];
            }
        }
        return Arrays.copyOf(out, write);
    }

    private static byte[] escape(byte[] bytes, int offset, int length) {
        ByteBuf out = Unpooled.buffer(length + 2);
        for (int i = offset; i < offset + length; i++) {
            int value = bytes[i] & 0xFF;
            if (value == 0x7D) {
                out.writeByte(0x7D).writeByte(0x01);
            } else if (value == 0x7E) {
                out.writeByte(0x7D).writeByte(0x02);
            } else {
                out.writeByte(value);
            }
        }
        byte[] encoded = new byte[out.readableBytes()];
        out.readBytes(encoded);
        return encoded;
    }

    private static byte[] delimit(byte[] body) {
        byte[] frame = new byte[body.length + 2];
        frame[0] = 0x7E;
        System.arraycopy(body, 0, frame, 1, body.length);
        frame[frame.length - 1] = 0x7E;
        return frame;
    }

    public record ParsedCommand(
            int messageId,
            int props,
            int protocolVersion,
            String terminalId,
            int originalSequence,
            int sequenceOffset,
            byte[] body
    ) {
        public ParsedCommand {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
