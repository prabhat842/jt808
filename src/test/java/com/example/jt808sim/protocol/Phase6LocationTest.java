package com.example.jt808sim.protocol;

import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.messages.LocationReportMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that video alarm additional info items 0x14–0x18 (Table 13, JT/T 1078-2016)
 * are correctly encoded in 0x0200 location reports.
 */
class Phase6LocationTest {

    private static final Coordinate POS = new Coordinate(31.23, 121.47);

    /** Encode a location body and parse all TLV additional-info items. */
    private static Map<Integer, ByteBuf> parseAdditionalInfo(VehicleState vs) {
        ByteBuf buf = Unpooled.buffer();
        LocationReportMessage.encodeLocationBody(buf, POS, 60.0, 90, Instant.now(), vs);
        buf.skipBytes(28); // fixed 28-byte basic info block
        Map<Integer, ByteBuf> items = new HashMap<>();
        while (buf.readableBytes() >= 2) {
            int id = buf.readUnsignedByte();
            int len = buf.readUnsignedByte();
            items.put(id, buf.readSlice(len).copy());
        }
        return items;
    }

    @Test
    void item0x14PresentWhenVideoAlarmWordNonZero() {
        VehicleState vs = new VehicleState();
        vs.setVideoAlarmWord(0x01); // video signal loss alarm bit

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x14), "Expected item 0x14 in location report");
        assertEquals(4, items.get(0x14).readableBytes());
        assertEquals(0x01, items.get(0x14).readUnsignedInt());
    }

    @Test
    void item0x14AbsentWhenVideoAlarmWordZero() {
        VehicleState vs = new VehicleState();
        // videoAlarmWord defaults to 0
        assertFalse(parseAdditionalInfo(vs).containsKey(0x14));
    }

    @Test
    void item0x15PresentWhenSignalLostChannelsNonZero() {
        VehicleState vs = new VehicleState();
        vs.setVideoSignalLostChannels(0b101); // channels 1 and 3

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x15));
        assertEquals(4, items.get(0x15).readableBytes());
        assertEquals(0b101L, items.get(0x15).readUnsignedInt());
    }

    @Test
    void item0x16PresentWhenShieldChannelsNonZero() {
        VehicleState vs = new VehicleState();
        vs.setVideoShieldChannels(0x0F); // channels 1–4 blocked

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x16));
        assertEquals(0x0FL, items.get(0x16).readUnsignedInt());
    }

    @Test
    void item0x17PresentWhenMemoryFailMaskNonZero() {
        VehicleState vs = new VehicleState();
        vs.setMemoryFailMask(0x0001); // memory slot 1 failed

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x17));
        assertEquals(2, items.get(0x17).readableBytes());
        assertEquals(0x0001, items.get(0x17).readUnsignedShort());
    }

    @Test
    void item0x18PresentWhenAbnormalDrivingFlagged() {
        VehicleState vs = new VehicleState();
        vs.setAbnormalDrivingBehavior(0x01); // fatigue bit
        vs.setFatigueDegree(75);

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x18));
        ByteBuf body = items.get(0x18);
        assertEquals(3, body.readableBytes());
        assertEquals(0x01, body.readUnsignedShort()); // behavior type flags
        assertEquals(75, body.readUnsignedByte());    // fatigue degree
    }

    @Test
    void item0x18PresentWhenOnlyFatigueDegreeNonZero() {
        VehicleState vs = new VehicleState();
        vs.setFatigueDegree(50);
        assertTrue(parseAdditionalInfo(vs).containsKey(0x18));
    }

    @Test
    void item0x18AbsentWhenNoDrivingAlarm() {
        VehicleState vs = new VehicleState();
        assertFalse(parseAdditionalInfo(vs).containsKey(0x18));
    }

    @Test
    void multipleVideoAlarmsEncodedTogether() {
        VehicleState vs = new VehicleState();
        vs.setVideoAlarmWord(0x03);
        vs.setVideoSignalLostChannels(0x01);
        vs.setVideoShieldChannels(0x02);
        vs.setMemoryFailMask(0x01);
        vs.setAbnormalDrivingBehavior(0x02);
        vs.setFatigueDegree(60);

        Map<Integer, ByteBuf> items = parseAdditionalInfo(vs);

        assertTrue(items.containsKey(0x14));
        assertTrue(items.containsKey(0x15));
        assertTrue(items.containsKey(0x16));
        assertTrue(items.containsKey(0x17));
        assertTrue(items.containsKey(0x18));
    }
}
