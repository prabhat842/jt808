package com.example.jt808sim.jt1078;

import com.example.jt808sim.jt1078.messages.AudioVideoAttributesMessage;
import com.example.jt808sim.jt1078.messages.ResourceListEntry;
import com.example.jt808sim.jt1078.messages.ResourceListUploadMessage;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jt1078ProtocolTest {
    @Test
    void decodesRealTimeRequest() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(9);
        body.writeBytes("127.0.0.1".getBytes(Jt808CodecSupport.GBK));
        body.writeShort(1078);
        body.writeShort(0);
        body.writeByte(3);
        body.writeByte(1);
        body.writeByte(0);

        Jt1078Command.RealTimeRequest request = assertInstanceOf(
                Jt1078Command.RealTimeRequest.class,
                Jt1078CommandDecoder.decode(MessageIds.JT1078_REALTIME_REQUEST, body));

        assertEquals("127.0.0.1", request.host());
        assertEquals(1078, request.tcpPort());
        assertEquals(3, request.channel());
        assertEquals(1, request.dataType());
        assertEquals(0, request.streamType());
    }

    @Test
    void decodesPlaybackRequest() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(9);
        body.writeBytes("127.0.0.1".getBytes(Jt808CodecSupport.GBK));
        body.writeShort(1078);
        body.writeShort(0);
        body.writeByte(1);
        body.writeByte(2);
        body.writeByte(1);
        body.writeByte(1);
        body.writeByte(0);
        body.writeByte(0);
        body.writeZero(6);
        body.writeZero(6);

        Jt1078Command.PlaybackRequest request = assertInstanceOf(
                Jt1078Command.PlaybackRequest.class,
                Jt1078CommandDecoder.decode(MessageIds.JT1078_PLAYBACK_REQUEST, body));

        assertEquals("127.0.0.1", request.host());
        assertEquals(1, request.channel());
        assertEquals(2, request.audioVideoType());
        assertEquals(1, request.streamType());
    }

    @Test
    void mediaPacketUsesJt1078Table19Layout() {
        ByteBuf packet = Unpooled.buffer();
        Jt1078Frame frame = new Jt1078Frame(
                Jt1078FrameType.VIDEO_P,
                280,
                false,
                0,
                40,
                new byte[20]);

        new Jt1078MediaPacket(
                "00000000000000000001",
                2,
                7,
                frame,
                Jt1078MediaPacket.Subpackage.ATOMIC,
                frame.payload()).encode(packet);

        assertEquals(0x30316364, packet.readInt());
        assertEquals(0x81, packet.readUnsignedByte());
        assertEquals(0xE2, packet.readUnsignedByte());
        assertEquals(7, packet.readUnsignedShort());
        assertEquals("000000000001", Jt808CodecSupport.readBcdDigits(packet, 6));
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(0x10, packet.readUnsignedByte());
        assertEquals(280, packet.readLong());
        assertEquals(0, packet.readUnsignedShort());
        assertEquals(40, packet.readUnsignedShort());
        assertEquals(20, packet.readUnsignedShort());
        assertEquals(20, packet.readableBytes());
    }

    @Test
    void packetizerFragmentsLargeFrame() {
        Jt1078Frame frame = new Jt1078Frame(
                Jt1078FrameType.VIDEO_I,
                400,
                true,
                0,
                40,
                new byte[25]);

        var packets = Jt1078Packetizer.packetize("00000000000000000001", 1, 10, frame, 10);

        assertEquals(3, packets.size());

        ByteBuf first = Unpooled.buffer();
        packets.get(0).encode(first);
        first.skipBytes(4 + 1 + 1 + 2 + 6 + 1);
        assertEquals(0x01, first.readUnsignedByte());
        first.skipBytes(8 + 2 + 2);
        assertEquals(10, first.readUnsignedShort());

        ByteBuf last = Unpooled.buffer();
        packets.get(2).encode(last);
        last.skipBytes(4 + 1 + 1 + 2 + 6 + 1);
        assertEquals(0x02, last.readUnsignedByte());
        last.skipBytes(8 + 2 + 2);
        assertEquals(5, last.readUnsignedShort());
        assertFalse(last.readableBytes() == 0);
    }

    @Test
    void realTimeRequestMapsTalkModeToAudioUplinkAndDownlink() {
        Jt1078Command.RealTimeRequest request = new Jt1078Command.RealTimeRequest("127.0.0.1", 1078, 0, 1, 2, 0);

        Jt1078SessionRequest sessionRequest = Jt1078SessionRequest.fromRealTimeRequest(request);

        assertEquals(Jt1078Command.RealTimeRequest.Mode.TALK, request.mode());
        assertEquals(1, sessionRequest.channel());
        assertTrue(sessionRequest.uplinkAudio());
        assertTrue(sessionRequest.downlinkAudio());
        assertFalse(sessionRequest.uplinkVideo());
        assertEquals(Jt1078FrameType.AUDIO, sessionRequest.preferredFrameType());
    }

    @Test
    void realTimeRequestMapsListenModeToDownlinkOnly() {
        Jt1078Command.RealTimeRequest request = new Jt1078Command.RealTimeRequest("127.0.0.1", 1078, 0, 1, 3, 0);

        Jt1078SessionRequest sessionRequest = Jt1078SessionRequest.fromRealTimeRequest(request);

        assertEquals(Jt1078Command.RealTimeRequest.Mode.LISTEN, request.mode());
        assertFalse(sessionRequest.hasUplinkMedia());
        assertTrue(sessionRequest.downlinkAudio());
    }

    @Test
    void realTimeRequestMapsPassThroughModeToPassThroughFrames() {
        Jt1078Command.RealTimeRequest request = new Jt1078Command.RealTimeRequest("127.0.0.1", 1078, 0, 1, 5, 0);

        Jt1078SessionRequest sessionRequest = Jt1078SessionRequest.fromRealTimeRequest(request);

        assertEquals(Jt1078Command.RealTimeRequest.Mode.PASSTHROUGH, request.mode());
        assertTrue(sessionRequest.hasUplinkMedia());
        assertEquals(Jt1078FrameType.PASSTHROUGH, sessionRequest.preferredFrameType());
    }

    @Test
    void playbackRequestPreservesPlaybackModeAndSpeed() {
        Jt1078Command.PlaybackRequest request = new Jt1078Command.PlaybackRequest(
                "127.0.0.1",
                1078,
                0,
                1,
                2,
                1,
                1,
                3,
                4,
                new byte[6],
                new byte[6]);

        Jt1078SessionRequest sessionRequest = Jt1078SessionRequest.fromPlaybackRequest(request);

        assertTrue(sessionRequest.playbackMode());
        assertEquals(3, sessionRequest.playbackControlType());
        assertEquals(4, sessionRequest.playbackSpeed());
    }

    @Test
    void audioVideoAttributesReflectConfiguredCapabilities() {
        ByteBuf body = Unpooled.buffer();
        Jt1078MediaConfig config = new Jt1078MediaConfig(
                "127.0.0.1",
                1078,
                "camera",
                List.of(),
                950,
                25,
                new Jt1078MediaConfig.CaptureConfig(true, true, "/dev/video0", "default", 1280, 720, 25, 1200, 8000, 1, 32, "ffmpeg"),
                new Jt1078MediaConfig.TalkConfig(true, false, false, "tmp/jt1078-downlink", 200));

        new AudioVideoAttributesMessage(1, "00000000000000000001", config, List.of(1, 2)).encodeBody(body);

        assertEquals(19, body.readUnsignedByte());
        assertEquals(1, body.readUnsignedByte());
        assertEquals(0, body.readUnsignedByte());
        assertEquals(1, body.readUnsignedByte());
        assertEquals(160, body.readUnsignedShort());
        assertEquals(1, body.readUnsignedByte());
        assertEquals(98, body.readUnsignedByte());
        assertEquals(2, body.readUnsignedByte());
        assertEquals(2, body.readUnsignedByte());
    }

    @Test
    void resourceListUploadUsesTable22BodyShape() {
        ByteBuf body = Unpooled.buffer();
        Instant start = Instant.parse("2026-05-10T04:45:30Z");
        Instant end = Instant.parse("2026-05-10T04:50:30Z");

        new ResourceListUploadMessage(
                1,
                "00000000000000000001",
                77,
                List.of(new ResourceListEntry(1, start, end, 0, 0, 2, 1, 1, 256_000L)))
                .encodeBody(body);

        assertEquals(77, body.readUnsignedShort());
        assertEquals(1, body.readUnsignedInt());
        assertEquals(1, body.readUnsignedByte());
        assertEquals("260510101530", bcdTimestamp(body));
        assertEquals("260510102030", bcdTimestamp(body));
        assertEquals(0, body.readUnsignedInt());
        assertEquals(0, body.readUnsignedInt());
        assertEquals(2, body.readUnsignedByte());
        assertEquals(1, body.readUnsignedByte());
        assertEquals(1, body.readUnsignedByte());
        assertEquals(256_000L, body.readUnsignedInt());
    }

    private static String bcdTimestamp(ByteBuf body) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int value = body.readUnsignedByte();
            out.append(value >> 4).append(value & 0x0F);
        }
        return out.toString();
    }
}
