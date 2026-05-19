package com.example.jt808sim.fleet.geofence;

import java.time.Instant;

/**
 * Circle area from 0x8600 Set circle area (Table 57, JT808-2013).
 *
 * Latitude and longitude are stored with their sign already applied
 * (south latitude is negative, west longitude is negative).
 * radiusMeters is the circle radius in metres.
 * startTime/endTime are null when the area has no time window.
 * maxSpeedKph == 0 when the area has no speed limit.
 */
public record CircleArea(
        long          areaId,
        AreaAttribute attribute,
        double        centerLat,
        double        centerLon,
        long          radiusMeters,
        Instant       startTime,
        Instant       endTime,
        int           maxSpeedKph,
        int           overspeedDurationSeconds
) {
    public boolean hasTimeWindow()  { return attribute.hasTimeWindow() && startTime != null; }
    public boolean hasSpeedLimit()  { return attribute.hasSpeedLimit() && maxSpeedKph > 0; }
}
