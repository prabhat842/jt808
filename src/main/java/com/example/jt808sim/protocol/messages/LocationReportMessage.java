package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

public class LocationReportMessage extends AbstractJt808Message {
    private final Coordinate coordinate;
    private final double speedKph;
    private final int heading;
    private final Instant timestamp;

    public LocationReportMessage(int sequence, String terminalId, Coordinate coordinate, double speedKph, int heading, Instant timestamp) {
        super(sequence, terminalId, true);
        this.coordinate = coordinate;
        this.speedKph = speedKph;
        this.heading = heading;
        this.timestamp = timestamp;
    }

    @Override
    public int messageId() {
        return MessageIds.LOCATION_REPORT;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeInt(0);
        out.writeInt(0x00000002);
        out.writeInt((int) Math.round(coordinate.latitude() * 1_000_000));
        out.writeInt((int) Math.round(coordinate.longitude() * 1_000_000));
        out.writeShort(0);
        out.writeShort((int) Math.round(speedKph * 10));
        out.writeShort(Math.floorMod(heading, 360));
        Jt808CodecSupport.writeBcdTimestamp(out, timestamp);
    }
}
