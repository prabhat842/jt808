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
        int     satelliteCount     // 0x31 GNSS satellite count
) {
    public boolean positioned()  { return (stateBit & 0x00000002L) != 0; }
    public boolean doorLocked()  { return (stateBit & (1L << 12)) != 0; }
    public int     loadStatus()  { return (int)((stateBit >> 8) & 0x03); }

    public double absoluteLatitude()  { return Math.abs(latitude); }
    public double absoluteLongitude() { return Math.abs(longitude); }
}
