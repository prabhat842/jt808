package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * 0x0107 Check terminal attribute response (Table 20, JT808-2013).
 *
 * Reports terminal type, manufacturer, model, device serial, ICCID,
 * hardware/firmware versions, GNSS module capability, and communication module type.
 */
public class TerminalAttributeResponseMessage extends AbstractJt808Message {
    private static final String HARDWARE_VERSION = "1.0.0";
    private static final String FIRMWARE_VERSION = "1.0.0";

    // Terminal type bits: bit0=passenger, bit6=hard-disk video supported
    private static final int TERMINAL_TYPE = 0x0041; // passenger + hard-disk video

    // GNSS module: bit0=GPS
    private static final int GNSS_ATTR = 0x01;

    // Communication module: bit5=TD-LTE
    private static final int COMM_ATTR = 0x20;

    private final VehicleIdentity identity;

    public TerminalAttributeResponseMessage(int sequence, String terminalId, VehicleIdentity identity) {
        super(sequence, terminalId, true);
        this.identity = identity;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_ATTR_RESP;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(TERMINAL_TYPE);
        Jt808CodecSupport.writeFixedAscii(out, identity.getManufacturerId(), 5);   // BYTE[5]
        Jt808CodecSupport.writeFixedAscii(out, identity.getTerminalModel(), 20);   // BYTE[20]
        // Terminal ID: BYTE[7] — last 7 digits of the 20-digit terminal ID
        String tid = identity.getTerminalId().replaceAll("\\D", "");
        Jt808CodecSupport.writeFixedAscii(out, tid.length() >= 7 ? tid.substring(tid.length() - 7) : tid, 7);
        // SIM card ICCID: BCD[10] — use terminal ID digits as synthetic ICCID
        Jt808CodecSupport.writeBcdDigits(out, identity.getTerminalId(), 10);
        // Hardware version: length BYTE + STRING
        byte[] hw = HARDWARE_VERSION.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(hw.length);
        out.writeBytes(hw);
        // Firmware version: length BYTE + STRING
        byte[] fw = FIRMWARE_VERSION.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(fw.length);
        out.writeBytes(fw);
        out.writeByte(GNSS_ATTR);
        out.writeByte(COMM_ATTR);
    }
}
