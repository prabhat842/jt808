package com.example.jt808sim.fleet.geofence;

/**
 * Turning point within a route from Table 68 (JT808-2013).
 *
 * widthMeters is the corridor half-width in metres from this point to the next.
 * tooLongThresholdSeconds and notEnoughThresholdSeconds are 0 when no time constraint.
 * maxSpeedKph is 0 when no speed limit on this segment.
 */
public record TurningPoint(
        long   pointId,
        double latitude,
        double longitude,
        int    widthMeters,
        int    tooLongThresholdSeconds,
        int    notEnoughThresholdSeconds,
        int    maxSpeedKph,
        int    overspeedDurationSeconds
) {
}
