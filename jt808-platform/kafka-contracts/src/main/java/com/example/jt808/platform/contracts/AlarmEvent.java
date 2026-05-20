package com.example.jt808.platform.contracts;

import java.time.Instant;

/**
 * Published to {@code telemetry.alarm} when an alarm bit transitions on or off.
 *
 * One event is emitted per bit per transition:
 *   - when a bit goes 0→1 (alarm opened): cleared==false
 *   - when a bit goes 1→0 (alarm cleared): cleared==true
 *
 * alarmType carries the bit number (0-31, Table 24 of JT808-2013).
 * warnBit carries the full 32-bit alarm word at the time of the transition.
 */
public record AlarmEvent(
        String  alarmId,
        String  vehicleId,
        String  terminalId,
        int     alarmType,      // bit number (0-31) from Table 24
        String  alarmName,      // human-readable name for this bit
        int     alarmLevel,     // severity: 1=warning 2=alarm 3=critical
        long    warnBit,        // full 32-bit alarm word at transition time
        double  latitude,
        double  longitude,
        double  speedKph,
        boolean cleared,        // true = alarm cleared, false = alarm opened
        Instant alarmTime,
        Instant receivedAt,
        // JT/T 1078-2016 Table 13 video alarm additional info (0 = absent/none)
        int     videoAlarmWord,             // 0x14 DWORD flag bits
        int     videoSignalLostChannels,    // 0x15 DWORD channel bitmask
        int     videoShieldChannels,        // 0x16 DWORD channel bitmask
        int     memoryFailMask,             // 0x17 WORD memory failure bitmask
        int     abnormalDrivingBehavior,    // 0x18 WORD type flags
        int     fatigueDegree,             // 0x18 BYTE 0-100
        // DMS alarm additional info (0x65 TLV, 0 = absent/none)
        int     dmsAlarmType,              // primary alarm type (1=fatigue, 2=distraction, 5=no_seatbelt)
        int     dmsFatigueDegree,          // fatigue level 0-10
        int     dmsAlarmFlags              // alarm condition bitmask
) {
}
