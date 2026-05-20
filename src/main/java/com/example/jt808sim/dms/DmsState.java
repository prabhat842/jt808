package com.example.jt808sim.dms;

/**
 * Current DMS alarm state, updated by {@link DmsPoller} at ~500 ms intervals.
 * Fields mirror the 0x65 TLV written into 0x0200 location reports.
 *
 * Alarm flag bitmask:
 *   bit 0  fatigue (eye closure)
 *   bit 1  distraction (face absent / looking away)
 *   bit 4  no seatbelt
 *   bit 5  camera blocked
 */
public class DmsState {

    public static final int ALARM_NONE        = 0;
    public static final int ALARM_FATIGUE     = 1;
    public static final int ALARM_DISTRACTION = 2;
    public static final int ALARM_PHONE       = 3;
    public static final int ALARM_SMOKING     = 4;
    public static final int ALARM_NO_SEATBELT = 5;
    public static final int ALARM_CAM_BLOCKED = 6;

    public static final int FLAG_FATIGUE      = 1;
    public static final int FLAG_DISTRACTION  = 1 << 1;
    public static final int FLAG_PHONE        = 1 << 2;
    public static final int FLAG_SMOKING      = 1 << 3;
    public static final int FLAG_NO_SEATBELT  = 1 << 4;
    public static final int FLAG_CAM_BLOCKED  = 1 << 5;

    private volatile int primaryAlarmType = ALARM_NONE;
    private volatile int fatigueDegree    = 0;   // 0-10
    private volatile int alarmFlags       = 0;

    public int  primaryAlarmType()  { return primaryAlarmType; }
    public int  fatigueDegree()     { return fatigueDegree; }
    public int  alarmFlags()        { return alarmFlags; }
    public boolean isActive()       { return alarmFlags != 0; }

    public void update(int primaryAlarm, int degree, int flags) {
        this.primaryAlarmType = primaryAlarm;
        this.fatigueDegree    = Math.max(0, Math.min(10, degree));
        this.alarmFlags       = flags;
    }

    public void clear() {
        this.primaryAlarmType = ALARM_NONE;
        this.fatigueDegree    = 0;
        this.alarmFlags       = 0;
    }
}
