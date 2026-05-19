package com.example.jt808.platform.protocol;

import java.time.Instant;

/**
 * Decoded 0x0200 location information report (JT808-2013 §8.18).
 *
 * Basic info (Table 23) is always present. Additional info items (Table 26/27)
 * are optional; absent items carry sentinel value -1 (mileage, fuel) or 0 (signal, satellites).
 */
public record TerminalLocationReport(
        long    warnBit,        // alarm sign, Table 24
        long    stateBit,       // status bits, Table 25
        double  latitude,       // degrees (south latitude when stateBit bit2 = 1)
        double  longitude,      // degrees (west longitude when stateBit bit3 = 1)
        int     altitudeMeters,
        double  speedKph,
        int     direction,
        Instant gpsTime,
        // Additional info items (Table 27) — -1 means absent
        long    mileageTenthKm,  // 0x01 odometer, 1/10 km
        int     fuelTenthLiters, // 0x02 fuel, 1/10 L
        int     signalStrength,  // 0x30 wireless signal 0-100
        int     satelliteCount   // 0x31 GNSS satellite count
) {
    /** Convenience: true when stateBit positioning bit (bit 1) is set. */
    public boolean positioned() {
        return (stateBit & 0x00000002L) != 0;
    }

    /** Returns lat/lon adjusted for hemisphere (always returns positive absolute value). */
    public double absoluteLatitude()  { return Math.abs(latitude); }
    public double absoluteLongitude() { return Math.abs(longitude); }
}
