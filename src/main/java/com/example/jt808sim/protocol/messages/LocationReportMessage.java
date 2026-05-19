package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

/**
 * 0x0200 Location information report (Tables 23-27, JT808-2013).
 *
 * Body layout:
 *   DWORD alarm sign    (Table 24 — 32 alarm bits)
 *   DWORD status        (Table 25 — 32 status bits)
 *   DWORD latitude      (absolute value × 1,000,000; S/W hemisphere in status word)
 *   DWORD longitude     (absolute value × 1,000,000)
 *   WORD  altitude      (metres)
 *   WORD  speed         (1/10 km/h)
 *   WORD  direction     (0-359°, 0=north clockwise)
 *   BCD[6] time         (YY-MM-DD-hh-mm-ss, GMT+8)
 *   Additional info items (Table 26/27): TLV list
 */
public class LocationReportMessage extends AbstractJt808Message {
    private final Coordinate coordinate;
    private final double speedKph;
    private final int heading;
    private final Instant timestamp;
    private final VehicleState vehicleState;

    public LocationReportMessage(int sequence, String terminalId, Coordinate coordinate,
                                 double speedKph, int heading, Instant timestamp,
                                 VehicleState vehicleState) {
        super(sequence, terminalId, true);
        this.coordinate = coordinate;
        this.speedKph = speedKph;
        this.heading = heading;
        this.timestamp = timestamp;
        this.vehicleState = vehicleState;
    }

    @Override
    public int messageId() {
        return MessageIds.LOCATION_REPORT;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        encodeLocationBody(out, coordinate, speedKph, heading, timestamp, vehicleState);
    }

    /**
     * Encodes the complete location body (basic info + additional info items).
     * Called directly by {@link LocationQueryResponseMessage} so the same layout
     * is reused without duplication.
     */
    public static void encodeLocationBody(ByteBuf out, Coordinate coordinate, double speedKph,
                                          int heading, Instant timestamp, VehicleState vs) {
        // Basic information (28 bytes fixed)
        out.writeInt((int) vs.alarmWord());
        out.writeInt((int) vs.statusWord(coordinate, speedKph));
        out.writeInt((int) Math.round(Math.abs(coordinate.latitude()) * 1_000_000));
        out.writeInt((int) Math.round(Math.abs(coordinate.longitude()) * 1_000_000));
        out.writeShort(vs.altitudeMeters());
        out.writeShort((int) Math.round(speedKph * 10));
        out.writeShort(Math.floorMod(heading, 360));
        Jt808CodecSupport.writeBcdTimestamp(out, timestamp);

        // Additional information items (Table 26/27) — TLV: ID(1) + len(1) + value
        // 0x01  Mileage, DWORD, 1/10 km
        out.writeByte(0x01);
        out.writeByte(4);
        out.writeInt((int) vs.odometerTenthKm());

        // 0x02  Fuel level, WORD, 1/10 L
        out.writeByte(0x02);
        out.writeByte(2);
        out.writeShort(vs.fuelTenthLiters());

        // 0x30  Wireless signal strength, BYTE
        out.writeByte(0x30);
        out.writeByte(1);
        out.writeByte(vs.signalStrength());

        // 0x31  GNSS positioning satellite count, BYTE
        out.writeByte(0x31);
        out.writeByte(1);
        out.writeByte(vs.satelliteCount());

        // 0x11  Overspeed additional info (Table 28) — only when overspeed or warning active
        // Body: location type BYTE; when type==0 (no specific area) no subsequent area ID field.
        long alarmWord = vs.alarmWord();
        boolean overspeedAlarmActive = (alarmWord & (1L << 1)) != 0 || (alarmWord & (1L << 13)) != 0;
        if (overspeedAlarmActive) {
            out.writeByte(0x11);
            out.writeByte(1);    // length: 1 byte (type only; area ID absent when type==0)
            out.writeByte(0);    // location type 0 = no specific position
        }
    }
}
