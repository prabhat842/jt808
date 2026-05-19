package com.example.jt808.platform.contracts;

import java.time.Instant;

/**
 * Published to {@code telemetry.gps} for every 0x0200 location report
 * and to {@code telemetry.gps} as a vehicle-state snapshot for 0x0500
 * vehicle control responses.
 *
 * Additional info fields carry -1 when the terminal did not include that item.
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
        int     vehicleSignalWord, // 0x25 Table 31
        int     ioStatus,          // 0x2A Table 32
        int     signalStrength,
        int     satelliteCount
) {
}
