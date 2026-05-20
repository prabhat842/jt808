package com.example.jt808.platform.protocol;

import java.time.Instant;

/**
 * Decoded 0x0200 location information report (JT808-2013 §8.18).
 * Also used as the embedded location body in 0x0500 vehicle control response.
 *
 * Basic info (Table 23) is always present. Additional info items (Table 26/27)
 * are optional; absent items carry sentinel value -1 (mileage, fuel) or 0 (others).
 *
 * Status word convenience helpers expose individual bits (Table 25):
 *   positioned()      — bit 1 (GNSS valid)
 *   doorLocked()      — bit 12
 *   loadStatus()      — bits 8-9 (0=empty 1=half 3=full)
 *
 * JT/T 1078-2016 Table 13 video alarm additional info (0 = absent/none):
 *   videoAlarmWord          — 0x14 DWORD flag bits (Table 14)
 *   videoSignalLostChannels — 0x15 DWORD bitmask (bit0=ch1...bit31=ch32)
 *   videoShieldChannels     — 0x16 DWORD bitmask
 *   memoryFailMask          — 0x17 WORD bitmask (bit0-11=main, bit12-15=DR)
 *   abnormalDrivingBehavior — 0x18 WORD flags (bit0=fatigue, bit1=call, bit2=smoking)
 *   fatigueDegree           — 0x18 BYTE 0-100
 *
 * DMS alarm additional info (0x65 TLV, 0 = absent/none):
 *   dmsAlarmType     — primary alarm type (1=fatigue, 2=distraction, 5=no_seatbelt, 6=cam_blocked)
 *   dmsFatigueDegree — fatigue level 0-10
 *   dmsAlarmFlags    — bitmask (bit0=fatigue, bit1=distraction, bit4=no_seatbelt, bit5=cam_blocked)
 */
public record TerminalLocationReport(
        long    warnBit,           // alarm sign, Table 24
        long    stateBit,          // status bits, Table 25
        double  latitude,
        double  longitude,
        int     altitudeMeters,
        double  speedKph,
        int     direction,
        Instant gpsTime,
        // Additional info items (Table 27) — -1 means absent
        long    mileageTenthKm,    // 0x01 odometer, 1/10 km
        int     fuelTenthLiters,   // 0x02 fuel, 1/10 L
        int     vehicleSignalWord, // 0x25 vehicle signal status (Table 31)
        int     ioStatus,          // 0x2A IO status (Table 32)
        int     signalStrength,    // 0x30 wireless signal strength
        int     satelliteCount,    // 0x31 GNSS satellite count
        // JT/T 1078-2016 Table 13 video alarm additional info (0 = absent/none)
        int     videoAlarmWord,             // 0x14 DWORD
        int     videoSignalLostChannels,    // 0x15 DWORD bitmask
        int     videoShieldChannels,        // 0x16 DWORD bitmask
        int     memoryFailMask,             // 0x17 WORD bitmask
        int     abnormalDrivingBehavior,    // 0x18 WORD type flags
        int     fatigueDegree,              // 0x18 BYTE 0-100
        // DMS alarm additional info (0x65 TLV, 0 = absent/none)
        int     dmsAlarmType,               // primary alarm type
        int     dmsFatigueDegree,           // fatigue level 0-10
        int     dmsAlarmFlags               // alarm condition bitmask
) {
    public boolean positioned()  { return (stateBit & 0x00000002L) != 0; }
    public boolean doorLocked()  { return (stateBit & (1L << 12)) != 0; }
    public int     loadStatus()  { return (int)((stateBit >> 8) & 0x03); }

    public double absoluteLatitude()  { return Math.abs(latitude); }
    public double absoluteLongitude() { return Math.abs(longitude); }

    public boolean hasVideoAlarms() {
        return videoAlarmWord != 0 || videoSignalLostChannels != 0
                || videoShieldChannels != 0 || memoryFailMask != 0
                || abnormalDrivingBehavior != 0 || fatigueDegree != 0;
    }

    public boolean hasDmsAlarms() {
        return dmsAlarmFlags != 0;
    }
}
