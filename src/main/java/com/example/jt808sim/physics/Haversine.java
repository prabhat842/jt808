package com.example.jt808sim.physics;

public final class Haversine {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private Haversine() {
    }

    public static double distanceMeters(Coordinate from, Coordinate to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static int bearingDegrees(Coordinate from, Coordinate to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        return (int) Math.round((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0);
    }

    public static Coordinate interpolate(Coordinate from, Coordinate to, double fraction) {
        if (fraction <= 0) {
            return from;
        }
        if (fraction >= 1) {
            return to;
        }
        return new Coordinate(
                from.latitude() + (to.latitude() - from.latitude()) * fraction,
                from.longitude() + (to.longitude() - from.longitude()) * fraction);
    }
}
