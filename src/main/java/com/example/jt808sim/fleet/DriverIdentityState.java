package com.example.jt808sim.fleet;

import java.time.Instant;

/**
 * Holds the currently identified driver associated with this terminal.
 *
 * The simulator creates a synthetic driver tied to the terminal's vehicle ID.
 * In real hardware, this comes from the physical IC card (驾驶证IC卡) reader.
 *
 * IC card reading result codes (Table 73, JT808-2013):
 *   0x00 = read success, 0x01 = card invalid, 0x02 = card expired, 0x03 = other error
 */
public class DriverIdentityState {

    public enum Status { CHECKED_IN, CHECKED_OUT }

    private Status status = Status.CHECKED_OUT;
    private Instant checkInTime;
    private Instant checkOutTime;

    private final String driverName;
    private final String certificateCode;    // 驾照号码 — driver's license number
    private final String issuingAuthority;   // 发证机关
    private final String expiryDateBcd;      // YYYYMMDD (4 BCD bytes)
    private final String driverIdentity;     // 从业资格证号 — professional certificate number

    public DriverIdentityState(String terminalId) {
        // Synthetic but deterministic driver data derived from terminal ID
        String suffix = terminalId.length() >= 6
                ? terminalId.substring(terminalId.length() - 6) : terminalId;
        this.driverName       = "Driver-" + suffix;
        this.certificateCode  = "DL" + suffix + "CN";
        this.issuingAuthority = "Shanghai DMV";
        this.expiryDateBcd    = "20281231";  // YYYYMMDD
        this.driverIdentity   = "PQ" + suffix + "0001";
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public void checkIn() {
        status      = Status.CHECKED_IN;
        checkInTime = Instant.now();
    }

    public void checkOut() {
        status       = Status.CHECKED_OUT;
        checkOutTime = Instant.now();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Status status()          { return status; }
    public boolean isCheckedIn()    { return status == Status.CHECKED_IN; }
    public Instant checkInTime()    { return checkInTime; }
    public Instant checkOutTime()   { return checkOutTime; }
    public String driverName()      { return driverName; }
    public String certificateCode() { return certificateCode; }
    public String issuingAuthority(){ return issuingAuthority; }
    public String expiryDateBcd()   { return expiryDateBcd; }  // "20281231"
    public String driverIdentity()  { return driverIdentity; }

    /** IC card reading result byte: 0x00 = success (always succeeds in simulation). */
    public int icCardReadResult()   { return 0x00; }
}
