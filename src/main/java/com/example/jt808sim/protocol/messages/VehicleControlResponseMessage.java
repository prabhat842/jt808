package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

/**
 * 0x0500 Vehicle control response (Table 55, JT808-2013).
 *
 * Body: response serial WORD + full location report body (same as 0x0200).
 * The platform determines control success from the door-locked status bit (bit 12)
 * in the embedded location status word.
 */
public class VehicleControlResponseMessage extends AbstractJt808Message {
    private final int responseSerial;
    private final Coordinate coordinate;
    private final double speedKph;
    private final int heading;
    private final Instant timestamp;
    private final VehicleState vehicleState;

    public VehicleControlResponseMessage(int sequence, String terminalId, int responseSerial,
                                          Coordinate coordinate, double speedKph, int heading,
                                          Instant timestamp, VehicleState vehicleState) {
        super(sequence, terminalId, true);
        this.responseSerial = responseSerial;
        this.coordinate = coordinate;
        this.speedKph = speedKph;
        this.heading = heading;
        this.timestamp = timestamp;
        this.vehicleState = vehicleState;
    }

    @Override
    public int messageId() {
        return MessageIds.VEHICLE_CONTROL_RESP;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSerial);
        LocationReportMessage.encodeLocationBody(out, coordinate, speedKph, heading, timestamp, vehicleState);
    }
}
