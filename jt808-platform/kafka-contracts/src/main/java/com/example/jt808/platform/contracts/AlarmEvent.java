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
        Instant receivedAt
) {
}
