package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.DriverIdentityState;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

/**
 * 0x0702 Driver identity information report (Table 73, JT808-2013).
 *
 * Sent when driver checks in (IC card inserted) or checks out (card removed).
 * Platform responds with 0x8702 carrying a platform timestamp for clock sync.
 *
 * Body layout:
 *   status          BYTE      0=check-in  1=check-out
 *   time            BCD[6]    event timestamp
 *   icReadResult    BYTE      0=success 1=invalid 2=expired 3=error
 *   driverName      BYTE(len) + GBK[len]
 *   certificateCode BYTE(len) + ASCII[len]   (driver's license number)
 *   authority       BYTE(len) + GBK[len]     (issuing authority)
 *   expiryDate      BCD[4]    YYYYMMDD
 *   driverIdentity  BYTE(len) + ASCII[len]   (professional certificate number)
 */
public class DriverIdentityReportMessage extends AbstractJt808Message {

    /** 0=check-in (card inserted), 1=check-out (card removed). */
    public static final int STATUS_CHECK_IN  = 0;
    public static final int STATUS_CHECK_OUT = 1;

    private final int status;
    private final Instant eventTime;
    private final DriverIdentityState driver;

    public DriverIdentityReportMessage(int sequence, String terminalId,
                                        int status, Instant eventTime,
                                        DriverIdentityState driver) {
        super(sequence, terminalId, true);
        this.status    = status;
        this.eventTime = eventTime;
        this.driver    = driver;
    }

    @Override public int messageId() { return MessageIds.DRIVER_IDENTITY_REPORT; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(status);
        Jt808CodecSupport.writeBcdTimestamp(out, eventTime);
        out.writeByte(driver.icCardReadResult());
        writeLenPrefixedGbk(out, driver.driverName());
        writeLenPrefixedAscii(out, driver.certificateCode());
        writeLenPrefixedGbk(out, driver.issuingAuthority());
        Jt808CodecSupport.writeBcdDigits(out, driver.expiryDateBcd(), 4);
        writeLenPrefixedAscii(out, driver.driverIdentity());
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
