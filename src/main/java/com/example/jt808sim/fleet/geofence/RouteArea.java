package com.example.jt808sim.fleet.geofence;

import java.time.Instant;
import java.util.List;

/**
 * Route from 0x8606 Set route (Tables 66-69, JT808-2013).
 *
 * A route is a ordered sequence of turning points defining a corridor.
 * startTime/endTime define the active window (null = always active).
 */
public record RouteArea(
        long             routeId,
        int              routeAttribute,
        Instant          startTime,
        Instant          endTime,
        List<TurningPoint> turningPoints
) {
    public boolean hasTimeWindow() {
        return (routeAttribute & 0x0001) != 0 && startTime != null;
    }
}
