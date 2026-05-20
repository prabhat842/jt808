package com.example.jt808.platform.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jt808FrameCodecTest {
    private final Jt808FrameCodec codec = new Jt808FrameCodec(ZoneId.of("Asia/Shanghai"));

    @Test
    void decodesLocationReport() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1);
        body.writeInt(2);
        body.writeInt(22_250_000);
        body.writeInt(72_200_000);
        body.writeShort(35);
        body.writeShort(456);
        body.writeShort(180);
        Jt808CodecSupport.writeBcdDigits(body, "260511165000", 6);

        DecodedJt808Message message = codec.decode(frame(MessageIds.LOCATION_REPORT, "00000000000000000001", 7, body));

        assertEquals(MessageIds.LOCATION_REPORT, message.header().messageId());
        TerminalLocationReport location = assertInstanceOf(TerminalLocationReport.class, message.body());
        assertEquals(22.25, location.latitude());
        assertEquals(72.2, location.longitude());
        assertEquals(45.6, location.speedKph());
        assertEquals(180, location.direction());
    }

    @Test
    void encodesDelimitedPlatformAck() {
        byte[] frame = codec.platformGeneralAck("00000000000000000001", 2, 7, MessageIds.LOCATION_REPORT, 0);

        assertEquals(0x7E, frame[0] & 0xFF);
        assertEquals(0x7E, frame[frame.length - 1] & 0xFF);
    }

    // ── Video alarm additional info (0x14–0x18) ───────────────────────────────

    @Test
    void decodesVideoAlarmAdditionalInfo() {
        ByteBuf body = locationBase();
        // 0x14 video alarm word = 0x03 (signal loss + blocking)
        body.writeByte(0x14); body.writeByte(4); body.writeInt(0x03);
        // 0x15 signal lost channels = 0x05 (ch1 and ch3)
        body.writeByte(0x15); body.writeByte(4); body.writeInt(0x05);
        // 0x16 shield channels = 0x02 (ch2)
        body.writeByte(0x16); body.writeByte(4); body.writeInt(0x02);
        // 0x17 memory fail mask = 0x01 (slot 1)
        body.writeByte(0x17); body.writeByte(2); body.writeShort(0x01);
        // 0x18 abnormal driving = 0x01 (fatigue), degree = 80
        body.writeByte(0x18); body.writeByte(3); body.writeShort(0x01); body.writeByte(80);

        DecodedJt808Message msg = codec.decode(frame(MessageIds.LOCATION_REPORT, "00000000000000000001", 1, body));
        TerminalLocationReport loc = assertInstanceOf(TerminalLocationReport.class, msg.body());

        assertEquals(0x03, loc.videoAlarmWord());
        assertEquals(0x05, loc.videoSignalLostChannels());
        assertEquals(0x02, loc.videoShieldChannels());
        assertEquals(0x01, loc.memoryFailMask());
        assertEquals(0x01, loc.abnormalDrivingBehavior());
        assertEquals(80,   loc.fatigueDegree());
        assertTrue(loc.hasVideoAlarms());
    }

    @Test
    void videoAlarmFieldsDefaultToZeroWhenAbsent() {
        DecodedJt808Message msg = codec.decode(frame(MessageIds.LOCATION_REPORT, "00000000000000000001", 1, locationBase()));
        TerminalLocationReport loc = assertInstanceOf(TerminalLocationReport.class, msg.body());

        assertEquals(0, loc.videoAlarmWord());
        assertEquals(0, loc.videoSignalLostChannels());
        assertEquals(0, loc.videoShieldChannels());
        assertEquals(0, loc.memoryFailMask());
        assertEquals(0, loc.abnormalDrivingBehavior());
        assertEquals(0, loc.fatigueDegree());
        assertFalse(loc.hasVideoAlarms());
    }

    // ── Multimedia event (0x0800) ─────────────────────────────────────────────

    @Test
    void decodesMultimediaEvent() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1_000_001);  // multimediaId
        body.writeByte(0);         // mediaType = image
        body.writeByte(0);         // formatCode = JPEG
        body.writeByte(3);         // eventCode = alarm
        body.writeByte(1);         // channelId = 1

        DecodedJt808Message msg = codec.decode(frame(MessageIds.MULTIMEDIA_EVENT, "00000000000000000001", 5, body));
        MultimediaUploadBody upload = assertInstanceOf(MultimediaUploadBody.class, msg.body());

        assertEquals(1_000_001L, upload.multimediaId());
        assertEquals(0, upload.mediaType());
        assertEquals(0, upload.formatCode());
        assertEquals(3, upload.eventCode());
        assertEquals(1, upload.channelId());
        assertNull(upload.location());
        assertFalse(upload.isDataUpload());
        assertEquals("jpg", upload.formatExtension());
    }

    // ── Multimedia data upload (0x0801) ───────────────────────────────────────

    @Test
    void decodesMultimediaDataUpload() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1_000_002);  // multimediaId
        body.writeByte(0);         // mediaType = image
        body.writeByte(0);         // formatCode = JPEG
        body.writeByte(1);         // eventCode = timing
        body.writeByte(2);         // channelId = 2
        body.writeBytes(locationBase());           // 28-byte location body
        body.writeBytes(new byte[]{1, 2, 3, 4, 5}); // 5-byte synthetic payload

        DecodedJt808Message msg = codec.decode(frame(MessageIds.MULTIMEDIA_DATA_UPLOAD, "00000000000000000001", 6, body));
        MultimediaUploadBody upload = assertInstanceOf(MultimediaUploadBody.class, msg.body());

        assertEquals(1_000_002L, upload.multimediaId());
        assertEquals(2, upload.channelId());
        assertNotNull(upload.location());
        assertTrue(upload.isDataUpload());
        assertEquals(5, upload.payloadBytes());
    }

    // ── 0x8800 multimedia upload ack ─────────────────────────────────────────

    @Test
    void encodesMultimediaUploadAck() {
        byte[] frame = codec.multimediaUploadAck("00000000000000000001", 3, 1_000_001L);

        assertEquals(0x7E, frame[0] & 0xFF);
        assertEquals(0x7E, frame[frame.length - 1] & 0xFF);
        // Frame is delimited; inner content starts at index 1
        // Verify the message ID is 0x8800 at bytes 1-2
        ByteBuf raw = Unpooled.wrappedBuffer(frame, 1, frame.length - 2);
        assertEquals(0x8800, raw.readUnsignedShort()); // messageId
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ByteBuf locationBase() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(0);           // warnBit
        body.writeInt(2);           // stateBit (positioned=true)
        body.writeInt(22_250_000);  // latitude
        body.writeInt(72_200_000);  // longitude
        body.writeShort(35);        // altitude
        body.writeShort(100);       // speed (10.0 kph)
        body.writeShort(90);        // direction
        Jt808CodecSupport.writeBcdDigits(body, "260511165000", 6); // gpsTime
        return body;
    }

    private static byte[] frame(int messageId, String terminalId, int sequence, ByteBuf body) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeShort(messageId);
        packet.writeShort(Jt808Header.bodyProperties(body.readableBytes(), true));
        packet.writeByte(1);
        Jt808CodecSupport.writeBcdDigits(packet, terminalId, 10);
        packet.writeShort(sequence);
        packet.writeBytes(body, body.readerIndex(), body.readableBytes());
        packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));
        byte[] raw = new byte[packet.readableBytes()];
        packet.readBytes(raw);
        return raw;
    }
}
