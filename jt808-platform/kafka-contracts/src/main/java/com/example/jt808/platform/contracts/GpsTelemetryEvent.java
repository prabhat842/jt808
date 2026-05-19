package com.example.jt808.platform.contracts;

import java.time.Instant;

/**
 * Published to {@code telemetry.gps} for every 0x0200 location report.
 *
 * Additional info fields (mileage, fuel, signalStrength, satelliteCount) carry
 * -1 (for long/int) when the terminal did not include that item in the report.
 */
public record GpsTelemetryEvent(
        String  vehicleId,
        String  deviceId,
        String  terminalId,
        Instant gpsTime,
        double  latitude,
        double  longitude,
        int     altitudeMeters,
        double  speedKph,
        int     direction,
        long    stateBit,
        long    warnBit,
        boolean positioned,
        String  remoteAddress,
        Instant receivedAt,
        // Additional info items (Table 27) — -1 when absent
        long    mileageTenthKm,
        int     fuelTenthLiters,
        int     signalStrength,
        int     satelliteCount
) {
}
