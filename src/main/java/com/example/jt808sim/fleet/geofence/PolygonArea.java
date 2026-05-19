package com.example.jt808sim.fleet.geofence;

import com.example.jt808sim.physics.Coordinate;

import java.time.Instant;
import java.util.List;

/**
 * Polygon area from 0x8604 Set polygon area (Tables 63-64, JT808-2013).
 *
 * vertices contains at least 3 points in order (closing the polygon is implied).
 * Coordinates are signed (south=negative, west=negative).
 */
public record PolygonArea(
        long          areaId,
        AreaAttribute attribute,
        Instant       startTime,
        Instant       endTime,
        int           maxSpeedKph,
        int           overspeedDurationSeconds,
        List<Coordinate> vertices
) {
    public boolean hasTimeWindow() { return attribute.hasTimeWindow() && startTime != null; }
    public boolean hasSpeedLimit() { return attribute.hasSpeedLimit() && maxSpeedKph > 0; }
}
