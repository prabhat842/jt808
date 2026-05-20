package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.LocationBuffer.LocationSnapshot;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 0x0704 Bulk location data upload (Table 78, JT808-2013).
 *
 * Sent after the terminal reconnects and uploads locations buffered during
 * the offline period. Each item is a standard 28-byte basic location block.
 *
 * Body layout:
 *   itemCount WORD
 *   items:    [WORD(bodyLength) + location body(28 bytes)] × n
 */
public class BulkLocationUploadMessage extends AbstractJt808Message {

    private static final int LOCATION_BODY_LEN = 28;

    private final List<LocationSnapshot> snapshots;

    public BulkLocationUploadMessage(int sequence, String terminalId,
                                      List<LocationSnapshot> snapshots) {
        super(sequence, terminalId, true);
        this.snapshots = snapshots;
    }

    @Override public int messageId() { return MessageIds.BULK_LOCATION_UPLOAD; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(snapshots.size());
        for (LocationSnapshot s : snapshots) {
            out.writeShort(LOCATION_BODY_LEN);
            encodeBasicLocationBlock(out, s);
        }
    }

    static void encodeBasicLocationBlock(ByteBuf out, LocationSnapshot s) {
        out.writeInt((int) s.alarmWord());
        out.writeInt((int) s.statusWord());
        out.writeInt((int) Math.round(Math.abs(s.coordinate().latitude())  * 1_000_000));
        out.writeInt((int) Math.round(Math.abs(s.coordinate().longitude()) * 1_000_000));
        out.writeShort(s.altitudeMeters());
        out.writeShort((int) Math.round(s.speedKph() * 10));
        out.writeShort(Math.floorMod(s.heading(), 360));
        Jt808CodecSupport.writeBcdTimestamp(out, s.time());
    }
}
