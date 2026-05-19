package com.example.jt808sim.fleet;

import com.example.jt808sim.physics.Coordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 — status word completion, vehicle signals, door lock control.
 */
class VehicleStatePhase4Test {

    private VehicleState vs;

    @BeforeEach
    void setUp() {
        vs = new VehicleState();
    }

    // ── Status word — existing bits still correct ─────────────────────────────

    @Test
    void statusWord_positioningAlwaysSet() {
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertTrue((status & (1L << 1)) != 0, "bit 1 = positioning");
    }

    @Test
    void statusWord_accAndRunningSetWhenMoving() {
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 60);
        assertTrue((status & 1L) != 0,      "bit 0 = ACC on when speed > 0");
        assertTrue((status & (1L << 4)) != 0, "bit 4 = running when speed > 0");
    }

    @Test
    void statusWord_southLatSetForNegativeLat() {
        long status = vs.statusWord(new Coordinate(-33.9, 151.2), 0); // Sydney
        assertTrue((status & (1L << 2)) != 0, "bit 2 = south latitude");
    }

    @Test
    void statusWord_westLonSetForNegativeLon() {
        long status = vs.statusWord(new Coordinate(51.5, -0.1), 0); // London
        assertTrue((status & (1L << 3)) != 0, "bit 3 = west longitude");
    }

    // ── Load status (bits 8-9) ────────────────────────────────────────────────

    @Test
    void statusWord_loadStatusEmpty() {
        vs.setLoadStatus(0);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(0L, (status >> 8) & 0x03, "bits 8-9 = 0 for empty");
    }

    @Test
    void statusWord_loadStatusHalfLoad() {
        vs.setLoadStatus(1);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1L, (status >> 8) & 0x03, "bits 8-9 = 1 for half load");
    }

    @Test
    void statusWord_loadStatusFull() {
        vs.setLoadStatus(3);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(3L, (status >> 8) & 0x03, "bits 8-9 = 3 for full load");
    }

    // ── Oil line and circuit (bits 10-11) ─────────────────────────────────────

    @Test
    void statusWord_oilLineNormalByDefault() {
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(0, (status >> 10) & 1, "bit 10 = 0 when oil line normal");
    }

    @Test
    void statusWord_oilLineDisconnectSetsbit10() {
        vs.setOilLineDisconnect(true);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1, (status >> 10) & 1, "bit 10 = 1 when oil line disconnected");
    }

    @Test
    void statusWord_circuitDisconnectSetsBit11() {
        vs.setCircuitDisconnect(true);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1, (status >> 11) & 1, "bit 11 = 1 when circuit disconnected");
    }

    // ── Door lock (bit 12) ────────────────────────────────────────────────────

    @Test
    void statusWord_doorUnlockedByDefault() {
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(0, (status >> 12) & 1, "bit 12 = 0 when unlocked");
    }

    @Test
    void statusWord_doorLockSetsBit12() {
        vs.setDoorLocked(true);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1, (status >> 12) & 1, "bit 12 = 1 when locked");
        assertTrue(vs.isDoorLocked());
    }

    @Test
    void statusWord_doorUnlockClearsBit12() {
        vs.setDoorLocked(true);
        vs.setDoorLocked(false);
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(0, (status >> 12) & 1, "bit 12 = 0 after unlock");
    }

    // ── Door open states (bits 13-17) ─────────────────────────────────────────

    @Test
    void statusWord_door1OpenSetsBit13() {
        vs.setDoorOpen(0, true); // door index 0 → bit 13
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1, (status >> 13) & 1, "bit 13 = door 1 open");
    }

    @Test
    void statusWord_door5OpenSetsBit17() {
        vs.setDoorOpen(4, true); // door index 4 → bit 17
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1, (status >> 17) & 1, "bit 17 = door 5 open");
    }

    @Test
    void statusWord_doorsClosedByDefault() {
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        // bits 13-17 all zero
        assertEquals(0L, (status >> 13) & 0x1F, "bits 13-17 = 0 when all doors closed");
    }

    // ── GNSS mode (bits 18-21) ────────────────────────────────────────────────

    @Test
    void statusWord_gpsOnlyByDefault() {
        vs.setGnssMode(1); // bit0=GPS
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(1L, (status >> 18) & 0x0F, "bits 18-21 = 1 for GPS only");
    }

    @Test
    void statusWord_gpsPlusBeidou() {
        vs.setGnssMode(0x03); // GPS + Beidou
        long status = vs.statusWord(new Coordinate(30.0, 120.0), 0);
        assertEquals(3L, (status >> 18) & 0x0F, "bits 18-21 = 3 for GPS+Beidou");
    }

    // ── Vehicle signal state ──────────────────────────────────────────────────

    @Test
    void vehicleSignalState_brakeSetWhenDecelerating() {
        VehicleSignalState signals = new VehicleSignalState();
        Instant t = Instant.ofEpochSecond(1_700_100_000L); // arbitrary time
        signals.update(80, 90, t);    // first tick at 80 kph
        signals.update(20, 90, t);    // second tick: dropped 60 kph → braking
        assertTrue((signals.toSignalWord() & (1 << 4)) != 0, "bit 4 = brake signal");
    }

    @Test
    void vehicleSignalState_noBrakeWhenMaintainingSpeed() {
        VehicleSignalState signals = new VehicleSignalState();
        Instant t = Instant.ofEpochSecond(1_700_100_000L);
        signals.update(80, 90, t);
        signals.update(80, 90, t); // same speed
        assertEquals(0, (signals.toSignalWord() & (1 << 4)), "no brake when speed stable");
    }

    @Test
    void vehicleSignalState_rightIndicatorOnRightTurn() {
        VehicleSignalState signals = new VehicleSignalState();
        Instant t = Instant.ofEpochSecond(1_700_100_000L);
        signals.update(60, 10, t);    // heading 10°
        signals.update(60, 50, t);    // heading 50° → right turn by 40°
        assertTrue((signals.toSignalWord() & (1 << 2)) != 0, "bit 2 = right indicator");
    }

    @Test
    void vehicleSignalState_leftIndicatorOnLeftTurn() {
        VehicleSignalState signals = new VehicleSignalState();
        Instant t = Instant.ofEpochSecond(1_700_100_000L);
        signals.update(60, 90, t);
        signals.update(60, 30, t);    // heading 30° → turned left by 60°
        assertTrue((signals.toSignalWord() & (1 << 3)) != 0, "bit 3 = left indicator");
    }
}
