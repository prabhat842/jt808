package com.example.jt808sim.fleet;

import com.example.jt808sim.fleet.geofence.AreaAlarmInfo;
import com.example.jt808sim.fleet.geofence.AreaAttribute;
import com.example.jt808sim.fleet.geofence.CircleArea;
import com.example.jt808sim.fleet.geofence.GeofenceStore;
import com.example.jt808sim.fleet.geofence.PolygonArea;
import com.example.jt808sim.fleet.geofence.RectangleArea;
import com.example.jt808sim.fleet.geofence.RouteArea;
import com.example.jt808sim.fleet.geofence.TurningPoint;
import com.example.jt808sim.physics.Coordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeofenceStoreTest {

    private GeofenceStore store;
    private AlarmState alarmState;

    @BeforeEach
    void setUp() {
        store = new GeofenceStore("test-term");
        alarmState = new AlarmState();
    }

    // ── Geometry algorithms ───────────────────────────────────────────────────

    @Test
    void insideCircle_pointAtCentre() {
        assertTrue(GeofenceStore.insideCircle(new Coordinate(39.9, 116.4), 39.9, 116.4, 500));
    }

    @Test
    void insideCircle_pointJustInsideRadius() {
        // Beijing centre → point ~400 m north
        Coordinate p = new Coordinate(39.9036, 116.4); // ~400 m north of 39.9
        assertTrue(GeofenceStore.insideCircle(p, 39.9, 116.4, 500));
    }

    @Test
    void insideCircle_pointOutside() {
        Coordinate p = new Coordinate(40.0, 116.4); // ~11 km north
        assertFalse(GeofenceStore.insideCircle(p, 39.9, 116.4, 500));
    }

    @Test
    void insideRectangle_pointInside() {
        // rect: lat 39-40, lon 116-117
        assertTrue(GeofenceStore.insideRectangle(new Coordinate(39.5, 116.5), 40.0, 116.0, 39.0, 117.0));
    }

    @Test
    void insideRectangle_pointOutside() {
        assertFalse(GeofenceStore.insideRectangle(new Coordinate(41.0, 116.5), 40.0, 116.0, 39.0, 117.0));
    }

    @Test
    void insidePolygon_squareContainsPoint() {
        List<Coordinate> square = List.of(
                new Coordinate(39.0, 116.0),
                new Coordinate(40.0, 116.0),
                new Coordinate(40.0, 117.0),
                new Coordinate(39.0, 117.0));
        assertTrue(GeofenceStore.insidePolygon(new Coordinate(39.5, 116.5), square));
    }

    @Test
    void insidePolygon_outsideSquare() {
        List<Coordinate> square = List.of(
                new Coordinate(39.0, 116.0),
                new Coordinate(40.0, 116.0),
                new Coordinate(40.0, 117.0),
                new Coordinate(39.0, 117.0));
        assertFalse(GeofenceStore.insidePolygon(new Coordinate(41.0, 116.5), square));
    }

    @Test
    void distanceToSegment_pointOnSegmentMidpoint() {
        Coordinate a = new Coordinate(39.9, 116.3);
        Coordinate b = new Coordinate(39.9, 116.5);
        Coordinate mid = new Coordinate(39.9, 116.4);
        double dist = GeofenceStore.distanceToSegmentMeters(mid, a, b);
        assertTrue(dist < 10, "midpoint should be ~0m from segment, got " + dist);
    }

    @Test
    void distanceToSegment_pointOffSegment() {
        Coordinate a = new Coordinate(39.9, 116.3);
        Coordinate b = new Coordinate(39.9, 116.5);
        Coordinate p = new Coordinate(40.0, 116.4); // ~11 km north
        double dist = GeofenceStore.distanceToSegmentMeters(p, a, b);
        assertTrue(dist > 10_000, "point is far from segment");
    }

    // ── GeofenceStore evaluation ──────────────────────────────────────────────

    @Test
    void circleArea_entryFiresAlarmBit20() {
        // Area: 1 km radius centred at 39.9, 116.4
        AreaAttribute attr = new AreaAttribute(0x0008); // alertPlatformOnEntry = true
        CircleArea area = new CircleArea(1L, attr, 39.9, 116.4, 1000, null, null, 0, 0);
        store.setCircles(1, List.of(area));

        // First eval: outside → no alarm
        store.evaluate(new Coordinate(41.0, 116.4), 60, alarmState, Instant.now());
        assertFalse(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));

        // Second eval: inside → entry alarm
        store.evaluate(new Coordinate(39.9, 116.4), 60, alarmState, Instant.now());
        assertTrue(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));
        assertNotNull(store.latestAreaAlarmInfo());
        assertEquals(0, store.latestAreaAlarmInfo().direction()); // 0 = entered
        assertEquals(1L, store.latestAreaAlarmInfo().areaId());
        assertEquals(1, store.latestAreaAlarmInfo().locationType()); // 1 = circle
    }

    @Test
    void circleArea_exitFiresAlarmBit20() {
        AreaAttribute attr = new AreaAttribute(0x0020); // alertPlatformOnExit
        CircleArea area = new CircleArea(2L, attr, 39.9, 116.4, 1000, null, null, 0, 0);
        store.setCircles(1, List.of(area));

        // Start inside
        store.evaluate(new Coordinate(39.9, 116.4), 60, alarmState, Instant.now());
        alarmState.clearOnAckBits(); // ACK clears bit 20

        // Exit
        store.evaluate(new Coordinate(41.0, 116.4), 60, alarmState, Instant.now());
        assertTrue(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));
        assertEquals(1, store.latestAreaAlarmInfo().direction()); // 1 = exited
    }

    @Test
    void rectangleArea_entryAndExit() {
        AreaAttribute attr = new AreaAttribute(0x0028); // alertPlatform on entry+exit
        RectangleArea rect = new RectangleArea(3L, attr, 40.0, 116.0, 39.0, 117.0, null, null, 0, 0);
        store.setRectangles(1, List.of(rect));

        store.evaluate(new Coordinate(41.0, 116.5), 50, alarmState, Instant.now()); // outside
        assertFalse(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));

        store.evaluate(new Coordinate(39.5, 116.5), 50, alarmState, Instant.now()); // inside
        assertTrue(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));
        assertEquals(2, store.latestAreaAlarmInfo().locationType()); // 2 = rectangle
    }

    @Test
    void deleteAllCircles_clearsStore() {
        AreaAttribute attr = new AreaAttribute(0);
        store.setCircles(1, List.of(new CircleArea(10L, attr, 39.9, 116.4, 500, null, null, 0, 0)));
        store.deleteCircles(List.of()); // empty = delete all
        store.evaluate(new Coordinate(39.9, 116.4), 0, alarmState, Instant.now());
        assertFalse(alarmState.isActive(AlarmBit.AREA_ENTRY_EXIT));
    }

    @Test
    void areaSpeedLimit_setsEffectiveMaxSpeed() {
        AreaAttribute attr = new AreaAttribute(0x0002); // speed limit bit set
        CircleArea area = new CircleArea(5L, attr, 39.9, 116.4, 1000, null, null, 80, 10);
        store.setCircles(1, List.of(area));

        store.evaluate(new Coordinate(39.9, 116.4), 100, alarmState, Instant.now());
        assertEquals(80, store.effectiveMaxSpeedKph());
    }

    @Test
    void outsideAllAreas_effectiveMaxSpeedIsMinusOne() {
        store.evaluate(new Coordinate(50.0, 120.0), 60, alarmState, Instant.now());
        assertEquals(-1, store.effectiveMaxSpeedKph());
    }

    @Test
    void routeArea_offTrackFiresBit23() {
        TurningPoint a = new TurningPoint(1L, 39.9, 116.3, 100, 0, 0, 0, 0);
        TurningPoint b = new TurningPoint(2L, 39.9, 116.5, 100, 0, 0, 0, 0);
        RouteArea route = new RouteArea(1L, 0, null, null, List.of(a, b));
        store.setRoute(route);

        // Start on-route (initially assumed on-route)
        store.evaluate(new Coordinate(39.9, 116.4), 80, alarmState, Instant.now()); // on route
        assertFalse(alarmState.isActive(AlarmBit.OFF_TRACK));

        // Go off-route (far north of the route)
        store.evaluate(new Coordinate(41.0, 116.4), 80, alarmState, Instant.now());
        assertTrue(alarmState.isActive(AlarmBit.OFF_TRACK));
    }

    @Test
    void routeArea_returnsOnRoute_clearsBit23() {
        TurningPoint a = new TurningPoint(1L, 39.9, 116.3, 500, 0, 0, 0, 0);
        TurningPoint b = new TurningPoint(2L, 39.9, 116.5, 500, 0, 0, 0, 0);
        RouteArea route = new RouteArea(1L, 0, null, null, List.of(a, b));
        store.setRoute(route);

        store.evaluate(new Coordinate(39.9, 116.4), 80, alarmState, Instant.now()); // on-route
        store.evaluate(new Coordinate(41.0, 116.4), 80, alarmState, Instant.now()); // off-route → bit 23 set
        assertTrue(alarmState.isActive(AlarmBit.OFF_TRACK));

        store.evaluate(new Coordinate(39.9, 116.4), 80, alarmState, Instant.now()); // back on-route → clears
        assertFalse(alarmState.isActive(AlarmBit.OFF_TRACK));
    }
}
