package com.example.jt808sim.physics;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.config.VehicleIdentity;

import java.time.Duration;
import java.time.Instant;

public class TrajectoryEngine {
    private final Coordinate start;
    private final Coordinate target;
    private final double speedMetersPerSecond;
    private final double routeDistanceMeters;
    private final FleetConfig.RouteMode routeMode;
    private Instant routeStartedAt;

    public TrajectoryEngine(VehicleIdentity identity, FleetConfig.RouteMode routeMode, Instant routeStartedAt) {
        this.start = new Coordinate(identity.getStartLat(), identity.getStartLon());
        this.target = new Coordinate(identity.getTargetLat(), identity.getTargetLon());
        this.speedMetersPerSecond = identity.getSpeedKph() * 1000.0 / 3600.0;
        this.routeDistanceMeters = Math.max(1.0, Haversine.distanceMeters(start, target));
        this.routeMode = routeMode;
        this.routeStartedAt = routeStartedAt;
    }

    public Snapshot snapshot(Instant now) {
        double elapsedSeconds = Math.max(0.0, Duration.between(routeStartedAt, now).toMillis() / 1000.0);
        double traveled = elapsedSeconds * speedMetersPerSecond;
        double leg = traveled / routeDistanceMeters;
        boolean reverseLeg = false;
        double fraction;

        if (routeMode == FleetConfig.RouteMode.STOP) {
            fraction = Math.min(1.0, leg);
        } else if (routeMode == FleetConfig.RouteMode.LOOP) {
            fraction = leg % 1.0;
        } else {
            long wholeLegs = (long) Math.floor(leg);
            reverseLeg = wholeLegs % 2 == 1;
            fraction = leg - wholeLegs;
            if (reverseLeg) {
                fraction = 1.0 - fraction;
            }
        }

        Coordinate from = reverseLeg ? target : start;
        Coordinate to = reverseLeg ? start : target;
        Coordinate position = Haversine.interpolate(start, target, fraction);
        int heading = Haversine.bearingDegrees(from, to);
        return new Snapshot(position, heading, speedMetersPerSecond * 3.6);
    }

    public record Snapshot(Coordinate coordinate, int heading, double speedKph) {
    }
}
