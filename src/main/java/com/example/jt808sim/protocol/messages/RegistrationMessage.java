package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/**
 * 0x0100 Terminal registration (Table 7, JT808-2013).
 *
 * Field layout:
 *   WORD  province domain ID
 *   WORD  city/county domain ID
 *   BYTE[5]  manufacturer ID
 *   BYTE[20] terminal type (model)
 *   BYTE[7]  terminal ID (device serial, capital letters + digits)
 *   BYTE     license plate color (1=blue 2=yellow 0=unregistered)
 *   STRING   license plate number (or VIN when color==0)
 */
public class RegistrationMessage extends AbstractJt808Message {
    private final VehicleIdentity identity;

    public RegistrationMessage(int sequence, VehicleIdentity identity) {
        super(sequence, identity.getTerminalId(), false);
        this.identity = identity;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_REGISTER;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(0);                                                          // province domain ID
        out.writeShort(0);                                                          // city/county domain ID
        Jt808CodecSupport.writeFixedAscii(out, identity.getManufacturerId(), 5);    // manufacturer ID: BYTE[5]
        Jt808CodecSupport.writeFixedAscii(out, identity.getTerminalModel(), 20);    // terminal type:   BYTE[20]
        Jt808CodecSupport.writeFixedAscii(out, deviceSerial(identity), 7);          // terminal ID:     BYTE[7]
        out.writeByte(identity.getPlateColor());                                    // plate color:     BYTE
        out.writeBytes(plateOrVin(identity).getBytes(Jt808CodecSupport.GBK));       // plate/VIN:       STRING
    }

    /** Last 7 digits of the terminal ID used as the device serial in the body. */
    private static String deviceSerial(VehicleIdentity id) {
        String digits = id.getTerminalId().replaceAll("\\D", "");
        return digits.length() >= 7 ? digits.substring(digits.length() - 7) : digits;
    }

    /** When plate color is 0 (unregistered) encode the VIN; otherwise encode the plate number. */
    private static String plateOrVin(VehicleIdentity id) {
        return id.getPlateColor() == 0 ? id.getVin() : id.getPlateNumber();
    }
}
