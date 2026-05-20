package com.example.jt808sim.fleet;

import com.example.jt808sim.dms.DmsState;
import com.example.jt808sim.fleet.geofence.AreaAlarmInfo;
import com.example.jt808sim.physics.Coordinate;

/**
 * Real-time vehicle state encoded into every 0x0200 location report.
 *
 * Status word (Table 25) — all 32 bits:
 *   0   ACC on
 *   1   Positioning valid
 *   2   South latitude
 *   3   West longitude
 *   4   Running (speed > 0)
 *   5   Lat/lon encrypted (always 0)
 *   8-9 Load status (00=empty 01=half 03=full)
 *   10  Oil line disconnect (0=normal)
 *   11  Circuit disconnect (0=normal)
 *   12  Door locked (1=locked)
 *   13  Door 1 open (front door)
 *   14  Door 2 open (middle door)
 *   15  Door 3 open (back door)
 *   16  Door 4 open (driver seat door)
 *   17  Door 5 open (user-defined)
 *   18  GPS positioning
 *   19  Beidou positioning
 *   20  GLONASS positioning
 *   21  Galileo positioning
 *
 * Additional info items (Table 27):
 *   0x01 Mileage (DWORD 1/10 km)
 *   0x02 Fuel (WORD 1/10 L)
 *   0x11 Overspeed info (when alarm bits 1 or 13 active)
 *   0x12 Area/route alarm info (when alarm bits 20 or 21 active)
 *   0x25 Vehicle signal status (Table 31, DWORD)
 *   0x2A IO status (Table 32, WORD)
 *   0x30 Signal strength (BYTE)
 *   0x31 GNSS satellite count (BYTE)
 */
public class VehicleState {

    // ── Status word constant masks ────────────────────────────────────────────
    private static final long BIT_ACC         = 1L;
    private static final long BIT_POSITIONING = 1L << 1;
    private static final long BIT_SOUTH_LAT   = 1L << 2;
    private static final long BIT_WEST_LON    = 1L << 3;
    private static final long BIT_RUNNING     = 1L << 4;

    // ── Alarm word ────────────────────────────────────────────────────────────
    private volatile long alarmWord = 0L;

    // ── Odometer / fuel / signal quality ─────────────────────────────────────
    private volatile long odometerTenthKm  = 0L;
    private volatile int  fuelTenthLiters  = 800;  // start 80L
    private volatile int  signalStrength   = 85;
    private volatile int  satelliteCount   = 12;
    private volatile int  altitudeMeters   = 50;

    // ── Status word fields ────────────────────────────────────────────────────
    /** Bits 8-9: 0=empty 1=half 3=full */
    private volatile int  loadStatus       = 0;
    /** Bits 18-21 bitmask: bit0=GPS bit1=Beidou bit2=GLONASS bit3=Galileo */
    private volatile int  gnssMode         = 1;    // GPS only by default
    /** Bit 10: 0=normal line, 1=disconnected */
    private volatile boolean oilLineDisconnect  = false;
    /** Bit 11: 0=normal circuit, 1=disconnected */
    private volatile boolean circuitDisconnect  = false;
    /** Bit 12: 0=unlocked, 1=locked */
    private volatile boolean doorLocked         = false;
    /** Bits 13-17: door 1-5 open flags */
    private final boolean[] doorOpen = new boolean[5];

    // ── Additional info items 0x25 and 0x2A ──────────────────────────────────
    /** Additional info 0x25 vehicle signal status word (Table 31). */
    private volatile int vehicleSignalWord = 0;
    /** Additional info 0x2A IO status (Table 32): bit0=deep dormancy bit1=dormancy. */
    private volatile int ioStatus          = 0;

    // ── Area alarm info for 0x12 additional info ──────────────────────────────
    private volatile AreaAlarmInfo areaAlarmInfo;

    // ── Video alarm additional info (0x14–0x18, Table 13–15, JT/T 1078-2016) ──
    /** 0x14 — video alarm word (Table 14 bits: 0=signal loss, 1=blocking, 2=memory, …). */
    private volatile int videoAlarmWord = 0;
    /** 0x15 — video signal loss per logical channel (DWORD bitmask, bit0=ch1…bit31=ch32). */
    private volatile int videoSignalLostChannels = 0;
    /** 0x16 — video signal blocking per logical channel (DWORD bitmask). */
    private volatile int videoShieldChannels = 0;
    /** 0x17 — memory failure (WORD bitmask: bit0–bit11=main memory, bit12–bit15=DR storage). */
    private volatile int memoryFailMask = 0;
    /** 0x18 — abnormal driving behaviour type flags (Table 15 WORD: bit0=fatigue, bit1=call, bit2=smoking). */
    private volatile int abnormalDrivingBehavior = 0;
    /** 0x18 — degree of fatigue (0–100, per Table 15). */
    private volatile int fatigueDegree = 0;

    // ── DMS alarm state (0x65 TLV, populated by DmsPoller) ───────────────────
    private final DmsState dmsState = new DmsState();

    // ── Alarm word accessors ──────────────────────────────────────────────────

    public long alarmWord()               { return alarmWord; }
    public void setAlarmWord(long w)      { this.alarmWord = w; }

    // ── Odometer / fuel / signal ──────────────────────────────────────────────

    public long odometerTenthKm()         { return odometerTenthKm; }
    public void addDistanceMeters(double meters) {
        odometerTenthKm += Math.round(meters / 100.0);
    }
    public int fuelTenthLiters()          { return fuelTenthLiters; }
    public int signalStrength()           { return signalStrength; }
    public int satelliteCount()           { return satelliteCount; }
    public int altitudeMeters()           { return altitudeMeters; }

    // ── Status word field setters (called from TerminalSession) ───────────────

    public void setLoadStatus(int loadStatus)           { this.loadStatus = loadStatus; }
    public void setGnssMode(int gnssMode)               { this.gnssMode = gnssMode; }
    public void setOilLineDisconnect(boolean v)         { this.oilLineDisconnect = v; }
    public void setCircuitDisconnect(boolean v)         { this.circuitDisconnect = v; }
    public void setDoorLocked(boolean locked)           { this.doorLocked = locked; }
    public boolean isDoorLocked()                       { return doorLocked; }
    /** doorIndex 0-4 → bits 13-17 */
    public void setDoorOpen(int doorIndex, boolean open) {
        if (doorIndex >= 0 && doorIndex < 5) doorOpen[doorIndex] = open;
    }

    // ── Additional info accessors ─────────────────────────────────────────────

    public int vehicleSignalWord()        { return vehicleSignalWord; }
    public void setVehicleSignalWord(int w){ this.vehicleSignalWord = w; }
    public int ioStatus()                 { return ioStatus; }
    public AreaAlarmInfo areaAlarmInfo()  { return areaAlarmInfo; }
    public void setAreaAlarmInfo(AreaAlarmInfo info) { this.areaAlarmInfo = info; }

    // ── Video alarm accessors ─────────────────────────────────────────────────
    public int videoAlarmWord()               { return videoAlarmWord; }
    public void setVideoAlarmWord(int w)      { this.videoAlarmWord = w; }
    public int videoSignalLostChannels()      { return videoSignalLostChannels; }
    public void setVideoSignalLostChannels(int mask) { this.videoSignalLostChannels = mask; }
    public int videoShieldChannels()          { return videoShieldChannels; }
    public void setVideoShieldChannels(int mask)     { this.videoShieldChannels = mask; }
    public int memoryFailMask()               { return memoryFailMask; }
    public void setMemoryFailMask(int mask)   { this.memoryFailMask = mask; }
    public int abnormalDrivingBehavior()      { return abnormalDrivingBehavior; }
    public void setAbnormalDrivingBehavior(int flags) { this.abnormalDrivingBehavior = flags; }
    public int fatigueDegree()                { return fatigueDegree; }
    public void setFatigueDegree(int degree)  { this.fatigueDegree = Math.max(0, Math.min(100, degree)); }

    public DmsState dmsState()               { return dmsState; }

    // ── Status word computation ───────────────────────────────────────────────

    /**
     * Builds the full 32-bit status word from current coordinate and speed (Table 25).
     * Hemisphere bits are derived from coordinate sign so encoded lat/lon can always
     * be written as absolute values.
     */
    public long statusWord(Coordinate coordinate, double speedKph) {
        long status = BIT_POSITIONING;
        if (speedKph > 0) status |= BIT_ACC | BIT_RUNNING;
        if (coordinate.latitude()  < 0) status |= BIT_SOUTH_LAT;
        if (coordinate.longitude() < 0) status |= BIT_WEST_LON;

        // Bits 8-9: load status
        status |= ((long)(loadStatus & 0x03)) << 8;

        // Bit 10: oil line disconnect
        if (oilLineDisconnect)  status |= (1L << 10);
        // Bit 11: circuit disconnect
        if (circuitDisconnect)  status |= (1L << 11);
        // Bit 12: door locked
        if (doorLocked)         status |= (1L << 12);
        // Bits 13-17: door 1-5 open
        for (int i = 0; i < 5; i++) {
            if (doorOpen[i]) status |= (1L << (13 + i));
        }

        // Bits 18-21: GNSS mode (bit0=GPS bit1=Beidou bit2=GLONASS bit3=Galileo)
        status |= ((long)(gnssMode & 0x0F)) << 18;

        return status;
    }
}
