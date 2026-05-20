package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.DriverIdentityState;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

/**
 * 0x0701 Driver IC card data (Table 72, JT808-2013).
 *
 * Sent immediately after 0x0702 check-in when the IC card is successfully read.
 * Contains the full card data rather than just the identity summary in 0x0702.
 *
 * Body layout:
 *   status        BYTE      0=read success
 *   driverName    BYTE(len) + GBK[len]
 *   certificate   BYTE(len) + ASCII[len]
 *   authority     BYTE(len) + GBK[len]
 *   expiryDate    BCD[4]    YYYYMMDD
 *   checkInTime   BCD[6]    card-insertion timestamp
 */
public class DriverIcCardDataMessage extends AbstractJt808Message {

    private final DriverIdentityState driver;
    private final Instant checkInTime;

    public DriverIcCardDataMessage(int sequence, String terminalId,
                                    DriverIdentityState driver, Instant checkInTime) {
        super(sequence, terminalId, true);
        this.driver      = driver;
        this.checkInTime = checkInTime;
    }

    @Override public int messageId() { return MessageIds.DRIVER_IC_CARD_DATA; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(driver.icCardReadResult());
        writeLenPrefixedGbk(out, driver.driverName());
        writeLenPrefixedAscii(out, driver.certificateCode());
        writeLenPrefixedGbk(out, driver.issuingAuthority());
        Jt808CodecSupport.writeBcdDigits(out, driver.expiryDateBcd(), 4);
        Jt808CodecSupport.writeBcdTimestamp(out, checkInTime);
    }

    private static void writeLenPrefixedGbk(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(Jt808CodecSupport.GBK);
        out.writeByte(bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeLenPrefixedAscii(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeByte(bytes.length);
        out.writeBytes(bytes);
    }
}
