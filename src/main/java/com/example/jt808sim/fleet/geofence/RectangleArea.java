package com.example.jt808sim.fleet.geofence;

import java.time.Instant;

/**
 * Rectangle area from 0x8602 Set rectangle area (Table 61, JT808-2013).
 *
 * topLeftLat > bottomRightLat (north is top).
 * Coordinates are signed (south=negative, west=negative).
 */
public record RectangleArea(
        long          areaId,
        AreaAttribute attribute,
        double        topLeftLat,
        double        topLeftLon,
        double        bottomRightLat,
        double        bottomRightLon,
        Instant       startTime,
        Instant       endTime,
        int           maxSpeedKph,
        int           overspeedDurationSeconds
) {
    public boolean hasTimeWindow() { return attribute.hasTimeWindow() && startTime != null; }
    public boolean hasSpeedLimit() { return attribute.hasSpeedLimit() && maxSpeedKph > 0; }
}
