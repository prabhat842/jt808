package com.example.jt808sim.dms;

import com.example.jt808sim.fleet.VehicleState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmsPollerTest {

    // ── applyResponse (JSON parsing) ──────────────────────────────────────────

    @Test
    void parsesActiveAlarmState() throws Exception {
        VehicleState vs = new VehicleState();
        DmsPoller poller = new DmsPoller("http://localhost:7500", vs);

        poller.applyResponse("""
            {"faceDetected":false,"eyesClosed":false,"fatigueDegree":3,
             "distracted":true,"seatbeltWorn":true,"alarmFlags":2,"primaryAlarm":2}
            """);

        DmsState dms = vs.dmsState();
        assertEquals(2, dms.primaryAlarmType(), "primaryAlarm mismatch");
        assertEquals(3, dms.fatigueDegree(),    "fatigueDegree mismatch");
        assertEquals(2, dms.alarmFlags(),       "alarmFlags mismatch");
        assertTrue(dms.isActive());
    }

    @Test
    void parsesAllClearState() throws Exception {
        VehicleState vs = new VehicleState();
        DmsPoller poller = new DmsPoller("http://localhost:7500", vs);

        vs.dmsState().update(DmsState.ALARM_FATIGUE, 7, DmsState.FLAG_FATIGUE);
        poller.applyResponse("""
            {"faceDetected":true,"eyesClosed":false,"fatigueDegree":0,
             "distracted":false,"seatbeltWorn":true,"alarmFlags":0,"primaryAlarm":0}
            """);

        assertFalse(vs.dmsState().isActive());
        assertEquals(0, vs.dmsState().fatigueDegree());
    }

    @Test
    void parsesFatigueWithSeatbeltAlarm() throws Exception {
        VehicleState vs = new VehicleState();
        DmsPoller poller = new DmsPoller("http://localhost:7500", vs);

        // bit0=fatigue + bit4=no_seatbelt = 0x11
        poller.applyResponse("""
            {"faceDetected":true,"eyesClosed":true,"fatigueDegree":8,
             "distracted":false,"seatbeltWorn":false,"alarmFlags":17,"primaryAlarm":1}
            """);

        DmsState dms = vs.dmsState();
        assertEquals(DmsState.ALARM_FATIGUE, dms.primaryAlarmType());
        assertEquals(8, dms.fatigueDegree());
        assertEquals(17, dms.alarmFlags());
        assertTrue((dms.alarmFlags() & DmsState.FLAG_FATIGUE) != 0,     "fatigue flag missing");
        assertTrue((dms.alarmFlags() & DmsState.FLAG_NO_SEATBELT) != 0, "seatbelt flag missing");
    }

    @Test
    void clampsFatigueDegreeToTen() throws Exception {
        VehicleState vs = new VehicleState();
        DmsPoller poller = new DmsPoller("http://localhost:7500", vs);
        poller.applyResponse("""
            {"alarmFlags":1,"primaryAlarm":1,"fatigueDegree":99}
            """);
        assertEquals(10, vs.dmsState().fatigueDegree(), "degree should be clamped at 10");
    }

    // ── DmsState unit tests ───────────────────────────────────────────────────

    @Test
    void dmsStateIsInactiveByDefault() {
        DmsState state = new DmsState();
        assertFalse(state.isActive());
        assertEquals(DmsState.ALARM_NONE, state.primaryAlarmType());
        assertEquals(0, state.fatigueDegree());
        assertEquals(0, state.alarmFlags());
    }

    @Test
    void dmsStateClearResetsAllFields() {
        DmsState state = new DmsState();
        state.update(DmsState.ALARM_FATIGUE, 7, DmsState.FLAG_FATIGUE | DmsState.FLAG_NO_SEATBELT);
        assertTrue(state.isActive());

        state.clear();
        assertFalse(state.isActive());
        assertEquals(0, state.primaryAlarmType());
        assertEquals(0, state.fatigueDegree());
        assertEquals(0, state.alarmFlags());
    }
}
