package com.example.jt808sim.netty;

import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.inbound.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip decode tests for Phase 5 platform→terminal messages.
 */
class Phase5DecoderTest {

    private static final String TERMINAL_ID = "00000000000000000001";

    // ── TextInfo (0x8300) ─────────────────────────────────────────────────────

    @Test
    void decodesTextInfo() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(0x04);                            // sign: display on terminal
        body.writeBytes("Hello JT808".getBytes(Jt808CodecSupport.GBK));

        TextInfo info = decode(MessageIds.TEXT_INFO, body, TextInfo.class);
        assertEquals(0x04, info.sign());
        assertEquals("Hello JT808", info.text());
    }

    // ── EventSetting (0x8301) ─────────────────────────────────────────────────

    @Test
    void decodesEventSetting() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(1);   // settingType=upgrade
        body.writeByte(2);   // 2 items
        // item 1
        body.writeByte(10);  // eventId
        byte[] content1 = "Panic".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(content1.length);
        body.writeBytes(content1);
        // item 2
        body.writeByte(20);
        byte[] content2 = "Fatigue".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(content2.length);
        body.writeBytes(content2);

        EventSetting setting = decode(MessageIds.EVENT_SETTING, body, EventSetting.class);
        assertEquals(1, setting.settingType());
        assertEquals(2, setting.items().size());
        assertEquals(10, setting.items().get(0).eventId());
        assertEquals("Panic", setting.items().get(0).content());
        assertEquals(20, setting.items().get(1).eventId());
        assertEquals("Fatigue", setting.items().get(1).content());
    }

    // ── QuestionSend (0x8302) ─────────────────────────────────────────────────

    @Test
    void decodesQuestionSend() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(0x01);   // sign: emergency
        byte[] q = "Speed limit exceeded?".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(q.length);
        body.writeBytes(q);
        body.writeByte(2);      // 2 answers
        // answer 1
        body.writeByte(1);
        byte[] a1 = "Yes".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(a1.length);
        body.writeBytes(a1);
        // answer 2
        body.writeByte(2);
        byte[] a2 = "No".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(a2.length);
        body.writeBytes(a2);

        QuestionSend q2 = decode(MessageIds.QUESTION_SEND, body, QuestionSend.class);
        assertEquals(0x01, q2.sign());
        assertEquals("Speed limit exceeded?", q2.question());
        assertEquals(2, q2.answers().size());
        assertEquals(1, q2.answers().get(0).answerId());
        assertEquals("Yes", q2.answers().get(0).content());
        assertEquals(1, q2.firstAnswerId());
    }

    // ── InfoOnDemandMenuSetting (0x8303) ──────────────────────────────────────

    @Test
    void decodesInfoOnDemandMenu() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(1);  // settingType=upgrade
        body.writeByte(1);  // 1 item
        body.writeByte(3);  // infoType
        byte[] name = "Weather".getBytes(Jt808CodecSupport.GBK);
        body.writeShort(name.length);
        body.writeBytes(name);

        InfoOnDemandMenuSetting menu = decode(MessageIds.INFO_ON_DEMAND_MENU, body, InfoOnDemandMenuSetting.class);
        assertEquals(1, menu.settingType());
        assertEquals(1, menu.items().size());
        assertEquals(3, menu.items().get(0).infoType());
        assertEquals("Weather", menu.items().get(0).name());
    }

    // ── InfoService (0x8304) ──────────────────────────────────────────────────

    @Test
    void decodesInfoService() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(2);  // infoType=2 (weather)
        body.writeBytes("Sunny, 25°C".getBytes(Jt808CodecSupport.GBK));

        InfoService service = decode(MessageIds.INFO_SERVICE, body, InfoService.class);
        assertEquals(2, service.infoType());
        assertEquals("Sunny, 25°C", service.content());
    }

    // ── CallbackCommand (0x8400) ──────────────────────────────────────────────

    @Test
    void decodesCallbackCommand() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(0);   // sign=0 ordinary
        body.writeBytes("13800138000".getBytes(Jt808CodecSupport.GBK));

        CallbackCommand cmd = decode(MessageIds.CALLBACK, body, CallbackCommand.class);
        assertEquals(0, cmd.sign());
        assertEquals("13800138000", cmd.phoneNumber());
    }

    // ── PhoneBookSetting (0x8401) ─────────────────────────────────────────────

    @Test
    void decodesPhoneBookSetting() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(2);  // settingType=append
        body.writeByte(1);  // 1 contact
        body.writeByte(3);  // sign: both directions
        byte[] phone = "13912345678".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(phone.length);
        body.writeBytes(phone);
        byte[] name = "Zhang San".getBytes(Jt808CodecSupport.GBK);
        body.writeByte(name.length);
        body.writeBytes(name);

        PhoneBookSetting pb = decode(MessageIds.PHONE_BOOK_SETTING, body, PhoneBookSetting.class);
        assertEquals(2, pb.settingType());
        assertEquals(1, pb.contacts().size());
        PhoneBookSetting.ContactItem c = pb.contacts().get(0);
        assertEquals(3, c.sign());
        assertEquals("13912345678", c.phoneNumber());
        assertEquals("Zhang San", c.contactName());
    }

    // ── MultimediaUploadAck (0x8800) ──────────────────────────────────────────

    @Test
    void decodesMultimediaUploadAckComplete() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1_000_001);   // multimediaId
        body.writeByte(0);          // 0 resend packets → complete

        MultimediaUploadAck ack = decode(MessageIds.MULTIMEDIA_UPLOAD_ACK, body, MultimediaUploadAck.class);
        assertEquals(1_000_001L, ack.multimediaId());
        assertTrue(ack.isComplete());
        assertTrue(ack.resendPacketIds().isEmpty());
    }

    @Test
    void decodesMultimediaUploadAckPartial() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1_000_002);
        body.writeByte(2);          // 2 packets to resend
        body.writeShort(3);
        body.writeShort(5);

        MultimediaUploadAck ack = decode(MessageIds.MULTIMEDIA_UPLOAD_ACK, body, MultimediaUploadAck.class);
        assertFalse(ack.isComplete());
        assertEquals(List.of(3, 5), ack.resendPacketIds());
    }

    // ── CameraSnapshotCommand (0x8801) ────────────────────────────────────────

    @Test
    void decodesCameraSnapshotCommand() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(1);           // channelId
        body.writeShort(3);          // takenCommand=3 (take 3 photos)
        body.writeShort(5);          // intervalSeconds
        body.writeByte(0);           // savingSign=0 (real-time upload)
        body.writeByte(0x02);        // resolution=640×480
        body.writeByte(80);          // quality
        body.writeByte(50);          // brightness
        body.writeByte(50);          // contrast
        body.writeByte(50);          // saturation
        body.writeByte(50);          // chroma

        CameraSnapshotCommand cmd = decode(MessageIds.CAMERA_SNAPSHOT_CMD, body, CameraSnapshotCommand.class);
        assertEquals(1, cmd.channelId());
        assertEquals(3, cmd.takenCommand());
        assertEquals(3, cmd.photoCount());
        assertEquals(5, cmd.intervalSeconds());
        assertTrue(cmd.realtimeUpload());
        assertEquals(0x02, cmd.resolution());
    }

    // ── StoreMediaQuery (0x8802) ──────────────────────────────────────────────

    @Test
    void decodesStoreMediaQueryNoTimeConstraint() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(0);   // mediaType=image
        body.writeByte(0);   // channelId=all
        body.writeByte(0);   // eventCode=all
        // all-zero timestamps = no constraint
        body.writeZero(6);   // startTime
        body.writeZero(6);   // endTime

        StoreMediaQuery query = decode(MessageIds.STORE_MEDIA_QUERY, body, StoreMediaQuery.class);
        assertEquals(0, query.mediaType());
        assertEquals(0, query.channelId());
        assertNull(query.startTime());
        assertNull(query.endTime());
    }

    // ── StoreMediaUploadCmd (0x8803) ──────────────────────────────────────────

    @Test
    void decodesStoreMediaUploadCmd() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(0);   // mediaType=image
        body.writeByte(1);   // channelId=1
        body.writeByte(0);   // eventCode=platform command
        body.writeZero(6);   // startTime (no constraint)
        body.writeZero(6);   // endTime
        body.writeByte(1);   // deleteAfterUpload=yes

        StoreMediaUploadCmd cmd = decode(MessageIds.STORE_MEDIA_UPLOAD_CMD, body, StoreMediaUploadCmd.class);
        assertEquals(1, cmd.channelId());
        assertEquals(1, cmd.deleteAfterUpload());
        assertNull(cmd.startTime());
    }

    // ── SoundRecordCmd (0x8804) ───────────────────────────────────────────────

    @Test
    void decodesSoundRecordCmd() {
        ByteBuf body = Unpooled.buffer();
        body.writeByte(1);    // command=start
        body.writeShort(30);  // recordSeconds
        body.writeByte(1);    // storeSign=store
        body.writeByte(0);    // samplingRate=8kHz

        SoundRecordCmd cmd = decode(MessageIds.SOUND_RECORD_CMD, body, SoundRecordCmd.class);
        assertEquals(1, cmd.command());
        assertEquals(30, cmd.recordSeconds());
        assertEquals(1, cmd.storeSign());
        assertEquals(0, cmd.samplingRate());
    }

    // ── SingleMediaUploadCmd (0x8805) ─────────────────────────────────────────

    @Test
    void decodesSingleMediaUploadCmd() {
        ByteBuf body = Unpooled.buffer();
        body.writeInt(1_000_007);  // multimediaId
        body.writeByte(1);          // deleteSign=delete

        SingleMediaUploadCmd cmd = decode(MessageIds.SINGLE_MEDIA_UPLOAD_CMD, body, SingleMediaUploadCmd.class);
        assertEquals(1_000_007L, cmd.multimediaId());
        assertEquals(1, cmd.deleteSign());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static <T> T decode(int messageId, ByteBuf body, Class<T> type) {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));
        assertTrue(channel.writeInbound(serverFrame(messageId, body)));
        Jt808Message message = channel.readInbound();
        assertEquals(messageId, message.header().messageId());
        return assertInstanceOf(type, message.body());
    }

    private static ByteBuf serverFrame(int messageId, ByteBuf body) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeShort(messageId);
        packet.writeShort(0x4000 | body.readableBytes());
        packet.writeByte(1);  // protocol version
        Jt808CodecSupport.writeBcdDigits(packet, TERMINAL_ID, 10);
        packet.writeShort(1); // sequence
        packet.writeBytes(body, body.readerIndex(), body.readableBytes());
        packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));

        EmbeddedChannel outbound = new EmbeddedChannel(new Jt808EscapeCodec());
        assertTrue(outbound.writeOutbound(packet));
        ByteBuf frame = outbound.readOutbound();
        assertEquals(0x7E, frame.readUnsignedByte());
        return frame.readSlice(frame.readableBytes() - 1).retain();
    }
}
