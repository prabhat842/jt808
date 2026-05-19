package com.example.jt808sim.netty;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.RegistrationResponse;
import com.example.jt808sim.protocol.ServerAck;
import com.example.jt808sim.protocol.TerminalLocationReport;
import com.example.jt808sim.protocol.TerminalRegistration;
import com.example.jt808sim.protocol.messages.HeartbeatMessage;
import com.example.jt808sim.protocol.messages.RegistrationMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jt808CodecRoundTripTest {
    @Test
    void outboundMessageIsDelimitedEscapedAndChecksummed() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageEncoder());

        assertTrue(channel.writeOutbound(new HeartbeatMessage(7, "00000000000000000001")));
        ByteBuf frame = channel.readOutbound();

        assertEquals(0x7E, frame.readUnsignedByte());
        assertEquals(0x7E, frame.getUnsignedByte(frame.writerIndex() - 1));

        ByteBuf payload = unescape(frame.readSlice(frame.readableBytes() - 1));
        assertEquals(MessageIds.HEARTBEAT, payload.readUnsignedShort());
        int props = payload.readUnsignedShort();
        assertEquals(0x4000, props);
        assertEquals(1, payload.readUnsignedByte());
        assertEquals("00000000000000000001", Jt808CodecSupport.readBcdDigits(payload, 10));
        assertEquals(7, payload.readUnsignedShort());
        int checksumIndex = payload.writerIndex() - 1;
        assertEquals(payload.getUnsignedByte(checksumIndex), Jt808CodecSupport.xor(payload, 0, checksumIndex));
    }

    @Test
    void decodesServerAck() {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));

        ByteBuf body = Unpooled.buffer();
        body.writeShort(12);
        body.writeShort(MessageIds.TERMINAL_AUTH);
        body.writeByte(0);

        assertTrue(channel.writeInbound(serverFrame(MessageIds.SERVER_ACK, "00000000000000000001", 1, body)));
        Jt808Message message = channel.readInbound();

        assertEquals(MessageIds.SERVER_ACK, message.header().messageId());
        ServerAck ack = assertInstanceOf(ServerAck.class, message.body());
        assertEquals(12, ack.responseSequence());
        assertEquals(MessageIds.TERMINAL_AUTH, ack.responseMessageId());
        assertTrue(ack.success());
        assertEquals(1, metrics.inboundMessages().sum());
    }

    @Test
    void decodesRegistrationResponse() {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));

        ByteBuf body = Unpooled.buffer();
        body.writeShort(3);
        body.writeByte(0);
        body.writeBytes("token".getBytes(Jt808CodecSupport.GBK));

        assertTrue(channel.writeInbound(serverFrame(MessageIds.REGISTER_RESPONSE, "00000000000000000001", 1, body)));
        Jt808Message message = channel.readInbound();

        assertEquals(MessageIds.REGISTER_RESPONSE, message.header().messageId());
        RegistrationResponse response = assertInstanceOf(RegistrationResponse.class, message.body());
        assertEquals(3, response.responseSequence());
        assertTrue(response.success());
        assertEquals("token", response.authCode());
    }

    @Test
    void registrationBodyUses2019FieldLengths() {
        VehicleIdentity identity = new VehicleIdentity();
        identity.setTerminalId("00000000000000000001");
        identity.setManufacturerId("TEST1");
        identity.setVin("VIN00000000000001");
        identity.setPlateNumber("TEST-0001");

        ByteBuf body = Unpooled.buffer();
        new RegistrationMessage(1, identity).encodeBody(body);

        assertEquals(2 + 2 + 11 + 30 + 30 + 1 + "TEST-0001".getBytes(Jt808CodecSupport.GBK).length, body.readableBytes());
    }

    @Test
    void decodesTerminalRegistrationBody() {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));
        VehicleIdentity identity = new VehicleIdentity();
        identity.setTerminalId("00000000000000000001");
        identity.setManufacturerId("TEST1");
        identity.setVin("VIN00000000000001");
        identity.setPlateNumber("TEST-0001");

        ByteBuf body = Unpooled.buffer();
        new RegistrationMessage(5, identity).encodeBody(body);

        assertTrue(channel.writeInbound(serverFrame(MessageIds.TERMINAL_REGISTER, identity.getTerminalId(), 5, body)));
        Jt808Message message = channel.readInbound();

        TerminalRegistration registration = assertInstanceOf(TerminalRegistration.class, message.body());
        assertEquals("TEST1", registration.manufacturerId());
        assertEquals("VIN00000000000001", registration.terminalModel());
        assertEquals(identity.getTerminalId(), registration.terminalIdentifier());
        assertEquals(2, registration.plateColor());
        assertEquals("TEST-0001", registration.plateNumber());
    }

    @Test
    void decodesLocationReportBody() {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));

        ByteBuf body = Unpooled.buffer();
        body.writeInt(0x00000001);
        body.writeInt(0x00000002);
        body.writeInt(22_250_000);
        body.writeInt(72_200_000);
        body.writeShort(35);
        body.writeShort(456);
        body.writeShort(180);
        Jt808CodecSupport.writeBcdDigits(body, "260511135244", 6);

        assertTrue(channel.writeInbound(serverFrame(MessageIds.LOCATION_REPORT, "00000000000000000001", 9, body)));
        Jt808Message message = channel.readInbound();

        TerminalLocationReport location = assertInstanceOf(TerminalLocationReport.class, message.body());
        assertEquals(1, location.warnBit());
        assertEquals(2, location.stateBit());
        assertEquals(22.25, location.latitude());
        assertEquals(72.2, location.longitude());
        assertEquals(35, location.altitudeMeters());
        assertEquals(45.6, location.speedKph());
        assertEquals(180, location.direction());
        assertTrue(location.positioned());
    }

    private static ByteBuf serverFrame(int messageId, String terminalId, int sequence, ByteBuf body) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeShort(messageId);
        packet.writeShort(0x4000 | body.readableBytes());
        packet.writeByte(1);
        Jt808CodecSupport.writeBcdDigits(packet, terminalId, 10);
        packet.writeShort(sequence);
        packet.writeBytes(body, body.readerIndex(), body.readableBytes());
        packet.writeByte(Jt808CodecSupport.xor(packet, packet.readerIndex(), packet.writerIndex()));

        EmbeddedChannel outbound = new EmbeddedChannel(new Jt808EscapeCodec());
        assertTrue(outbound.writeOutbound(packet));
        ByteBuf frame = outbound.readOutbound();
        assertEquals(0x7E, frame.readUnsignedByte());
        return frame.readSlice(frame.readableBytes() - 1).retain();
    }

    private static ByteBuf unescape(ByteBuf frameBody) {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808EscapeCodec());
        assertTrue(channel.writeInbound(frameBody.retain()));
        return channel.readInbound();
    }
}
