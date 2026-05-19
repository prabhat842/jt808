package com.example.jt808sim.fleet.geofence;

import com.example.jt808sim.fleet.AlarmBit;
import com.example.jt808sim.fleet.AlarmState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.physics.Haversine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-terminal in-memory geofence store.
 *
 * Stores circle, rectangle, polygon, and route areas received via 0x8600–0x8606
 * commands. On each location tick, {@link #evaluate} checks all areas, detects
 * entry/exit transitions, updates the alarm state, and records the latest
 * {@link AreaAlarmInfo} for encoding in the 0x0200 additional info item 0x12.
 *
 * Setting attribute semantics (shared by circle/rect/poly):
 *   0 = Upgrade: replace all existing areas of this type
 *   1 = Append:  add/overwrite by area ID
 *   2 = Modify:  update by area ID, leave others unchanged
 */
public class GeofenceStore {
    private static final Logger log = LoggerFactory.getLogger(GeofenceStore.class);

    private final String terminalId;

    private final Map<Long, CircleArea>    circles    = new LinkedHashMap<>();
    private final Map<Long, RectangleArea> rectangles = new LinkedHashMap<>();
    private final Map<Long, PolygonArea>   polygons   = new LinkedHashMap<>();
    private final Map<Long, RouteArea>     routes     = new LinkedHashMap<>();

    // Inside-state tracking for entry/exit detection
    private final Map<Long, Boolean> wasInsideCircle = new HashMap<>();
    private final Map<Long, Boolean> wasInsideRect   = new HashMap<>();
    private final Map<Long, Boolean> wasInsidePoly   = new HashMap<>();
    private final Map<Long, Boolean> wasOnRoute      = new HashMap<>();

    // Result of last evaluate() — null when no area alarm fired
    private AreaAlarmInfo latestAreaAlarmInfo;

    // Minimum max speed across all speed-limited areas the terminal is currently inside.
    // -1 means "no area speed limit active, use global params".
    private int effectiveMaxSpeedKph = -1;

    public GeofenceStore(String terminalId) {
        this.terminalId = terminalId;
    }

    // ── Area CRUD ─────────────────────────────────────────────────────────────

    public void setCircles(int settingAttribute, List<CircleArea> areas) {
        applySettingAttribute(circles, areas, settingAttribute, CircleArea::areaId);
        log.info("terminal {} circles store: {} areas (attr={})", terminalId, circles.size(), settingAttribute);
    }

    public void deleteCircles(List<Long> ids) {
        if (ids.isEmpty()) {
            circles.clear(); wasInsideCircle.clear();
            log.info("terminal {} all circles deleted", terminalId);
        } else {
            ids.forEach(id -> { circles.remove(id); wasInsideCircle.remove(id); });
        }
    }

    public void setRectangles(int settingAttribute, List<RectangleArea> areas) {
        applySettingAttribute(rectangles, areas, settingAttribute, RectangleArea::areaId);
        log.info("terminal {} rectangles store: {} areas", terminalId, rectangles.size());
    }

    public void deleteRectangles(List<Long> ids) {
        if (ids.isEmpty()) {
            rectangles.clear(); wasInsideRect.clear();
        } else {
            ids.forEach(id -> { rectangles.remove(id); wasInsideRect.remove(id); });
        }
    }

    public void setPolygon(int settingAttribute, PolygonArea area) {
        if (settingAttribute == 0) { polygons.clear(); wasInsidePoly.clear(); }
        polygons.put(area.areaId(), area);
        log.info("terminal {} polygon {} set (attr={})", terminalId, area.areaId(), settingAttribute);
    }

    public void deletePolygons(List<Long> ids) {
        if (ids.isEmpty()) {
            polygons.clear(); wasInsidePoly.clear();
        } else {
            ids.forEach(id -> { polygons.remove(id); wasInsidePoly.remove(id); });
        }
    }

    public void setRoute(RouteArea route) {
        routes.put(route.routeId(), route);
        log.info("terminal {} route {} set ({} turning points)", terminalId, route.routeId(), route.turningPoints().size());
    }

    public void deleteRoutes(List<Long> ids) {
        if (ids.isEmpty()) {
            routes.clear(); wasOnRoute.clear();
        } else {
            ids.forEach(id -> { routes.remove(id); wasOnRoute.remove(id); });
        }
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Evaluates all stored areas against the current terminal position.
     * Detected entry/exit transitions update the alarm state and record the
     * latest {@link AreaAlarmInfo} for 0x12 additional info encoding.
     */
    public void evaluate(Coordinate position, double speedKph, AlarmState alarmState, Instant now) {
        latestAreaAlarmInfo = null;
        effectiveMaxSpeedKph = -1;

        evaluateCircles(position, speedKph, alarmState, now);
        evaluateRectangles(position, speedKph, alarmState, now);
        evaluatePolygons(position, speedKph, alarmState, now);
        evaluateRoutes(position, speedKph, alarmState, now);
    }

    /** Latest area/route alarm info to encode in 0x12 additional info item. Null when none. */
    public AreaAlarmInfo latestAreaAlarmInfo() {
        return latestAreaAlarmInfo;
    }

    /**
     * Effective maximum speed enforced by area speed limits at the current position.
     * Returns -1 when the terminal is not inside any speed-limited area.
     */
    public int effectiveMaxSpeedKph() {
        return effectiveMaxSpeedKph;
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    /** Point inside circle using great-circle distance. */
    public static boolean insideCircle(Coordinate p, double cLat, double cLon, long radiusMeters) {
        return Haversine.distanceMeters(p, new Coordinate(cLat, cLon)) <= radiusMeters;
    }

    /** Point inside axis-aligned bounding box. Works for any hemisphere combination. */
    public static boolean insideRectangle(Coordinate p, double topLeftLat, double topLeftLon,
                                   double bottomRightLat, double bottomRightLon) {
        double minLat = Math.min(topLeftLat, bottomRightLat);
        double maxLat = Math.max(topLeftLat, bottomRightLat);
        double minLon = Math.min(topLeftLon, bottomRightLon);
        double maxLon = Math.max(topLeftLon, bottomRightLon);
        return p.latitude() >= minLat && p.latitude() <= maxLat
                && p.longitude() >= minLon && p.longitude() <= maxLon;
    }

    /** Ray-casting point-in-polygon for arbitrary vertex list. */
    public static boolean insidePolygon(Coordinate p, List<Coordinate> vertices) {
        int n = vertices.size();
        if (n < 3) return false;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i).latitude(), yi = vertices.get(i).longitude();
            double xj = vertices.get(j).latitude(), yj = vertices.get(j).longitude();
            if (((yi > p.longitude()) != (yj > p.longitude())) &&
                    (p.latitude() < (xj - xi) * (p.longitude() - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Minimum great-circle distance from point p to the line segment [a, b].
     * Uses planar projection for the parameter t, then Haversine for the distance.
     * Accurate for segments shorter than a few hundred km.
     */
    public static double distanceToSegmentMeters(Coordinate p, Coordinate a, Coordinate b) {
        double ax = a.latitude(), ay = a.longitude();
        double bx = b.latitude(), by = b.longitude();
        double px = p.latitude(), py = p.longitude();
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0.0) return Haversine.distanceMeters(p, a);
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / lenSq));
        return Haversine.distanceMeters(p, new Coordinate(ax + t * dx, ay + t * dy));
    }

    // ── Private evaluation helpers ────────────────────────────────────────────

    private void evaluateCircles(Coordinate pos, double speedKph, AlarmState state, Instant now) {
        for (CircleArea area : circles.values()) {
            if (area.hasTimeWindow() && !isActiveNow(area.startTime(), area.endTime(), now)) continue;
            boolean inside = insideCircle(pos, area.centerLat(), area.centerLon(), area.radiusMeters());
            boolean was    = wasInsideCircle.getOrDefault(area.areaId(), false);
            wasInsideCircle.put(area.areaId(), inside);
            if (inside != was) {
                fireAreaAlarm(state, 1, area.areaId(), inside, area.attribute(), "circle");
            }
            if (inside && area.hasSpeedLimit()) {
                trackAreaMaxSpeed(area.maxSpeedKph());
            }
        }
    }

    private void evaluateRectangles(Coordinate pos, double speedKph, AlarmState state, Instant now) {
        for (RectangleArea area : rectangles.values()) {
            if (area.hasTimeWindow() && !isActiveNow(area.startTime(), area.endTime(), now)) continue;
            boolean inside = insideRectangle(pos, area.topLeftLat(), area.topLeftLon(),
                    area.bottomRightLat(), area.bottomRightLon());
            boolean was    = wasInsideRect.getOrDefault(area.areaId(), false);
            wasInsideRect.put(area.areaId(), inside);
            if (inside != was) {
                fireAreaAlarm(state, 2, area.areaId(), inside, area.attribute(), "rectangle");
            }
            if (inside && area.hasSpeedLimit()) {
                trackAreaMaxSpeed(area.maxSpeedKph());
            }
        }
    }

    private void evaluatePolygons(Coordinate pos, double speedKph, AlarmState state, Instant now) {
        for (PolygonArea area : polygons.values()) {
            if (area.hasTimeWindow() && !isActiveNow(area.startTime(), area.endTime(), now)) continue;
            boolean inside = insidePolygon(pos, area.vertices());
            boolean was    = wasInsidePoly.getOrDefault(area.areaId(), false);
            wasInsidePoly.put(area.areaId(), inside);
            if (inside != was) {
                fireAreaAlarm(state, 3, area.areaId(), inside, area.attribute(), "polygon");
            }
            if (inside && area.hasSpeedLimit()) {
                trackAreaMaxSpeed(area.maxSpeedKph());
            }
        }
    }

    private void evaluateRoutes(Coordinate pos, double speedKph, AlarmState state, Instant now) {
        for (RouteArea route : routes.values()) {
            if (route.hasTimeWindow() && !isActiveNow(route.startTime(), route.endTime(), now)) continue;
            List<TurningPoint> points = route.turningPoints();
            if (points.size() < 2) continue;

            boolean onRoute = false;
            for (int i = 0; i < points.size() - 1; i++) {
                TurningPoint a = points.get(i);
                TurningPoint b = points.get(i + 1);
                double distMeters = distanceToSegmentMeters(pos,
                        new Coordinate(a.latitude(), a.longitude()),
                        new Coordinate(b.latitude(), b.longitude()));
                if (distMeters <= a.widthMeters()) {
                    onRoute = true;
                    if (a.maxSpeedKph() > 0) trackAreaMaxSpeed(a.maxSpeedKph());
                    break;
                }
            }

            boolean was = wasOnRoute.getOrDefault(route.routeId(), true); // assume on-route initially
            wasOnRoute.put(route.routeId(), onRoute);

            if (!onRoute && was) {
                // Off-track: set alarm bit 23 (WHEN_RELIEVED)
                state.set(AlarmBit.OFF_TRACK);
                // Also set entry/exit bit 21 (exit transition)
                state.set(AlarmBit.ROUTE_ENTRY_EXIT);
                latestAreaAlarmInfo = new AreaAlarmInfo(4, route.routeId(), 1); // direction=1 (out)
                log.info("terminal {} off-track from route {}", terminalId, route.routeId());
            } else if (onRoute && !was) {
                // Returned to route: clear off-track alarm
                state.clear(AlarmBit.OFF_TRACK);
                state.set(AlarmBit.ROUTE_ENTRY_EXIT);
                latestAreaAlarmInfo = new AreaAlarmInfo(4, route.routeId(), 0); // direction=0 (in)
                log.info("terminal {} back on route {}", terminalId, route.routeId());
            }
        }
    }

    private void fireAreaAlarm(AlarmState state, int locationType, long areaId, boolean entered,
                                AreaAttribute attr, String areaKind) {
        int direction = entered ? 0 : 1;
        // Only fire platform alarm if area is configured to alert platform
        boolean shouldAlert = entered ? attr.alertPlatformOnEntry() : attr.alertPlatformOnExit();
        // Always fire in simulator (mirrors real terminal that alerts regardless)
        state.set(AlarmBit.AREA_ENTRY_EXIT);
        latestAreaAlarmInfo = new AreaAlarmInfo(locationType, areaId, direction);
        log.info("terminal {} {} {} area {} direction={}", terminalId,
                entered ? "entered" : "exited", areaKind, areaId, direction);
    }

    private void trackAreaMaxSpeed(int areaMaxSpeed) {
        if (effectiveMaxSpeedKph < 0 || areaMaxSpeed < effectiveMaxSpeedKph) {
            effectiveMaxSpeedKph = areaMaxSpeed;
        }
    }

    private static boolean isActiveNow(Instant start, Instant end, Instant now) {
        return (start == null || !now.isBefore(start)) && (end == null || !now.isAfter(end));
    }

    private static <T> void applySettingAttribute(Map<Long, T> store, List<T> areas,
                                                   int settingAttribute,
                                                   java.util.function.Function<T, Long> idFn) {
        if (settingAttribute == 0) store.clear(); // upgrade = replace all
        for (T area : areas) {
            store.put(idFn.apply(area), area); // append or overwrite
        }
    }
}
