package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlarmEngineTest {

    private AlarmEngine engine;
    private AlarmState alarmState;
    private TerminalParams params;

    @BeforeEach
    void setUp() {
        engine = new AlarmEngine("test-term");
        alarmState = new AlarmState();
        params = buildParams();
    }

    // ── Overspeed ─────────────────────────────────────────────────────────────

    @Test
    void overspeedWarningFiresImmediately() {
        // First tick at overspeed → warning set, full alarm not yet
        eval(130, 0);
        assertTrue(alarmState.isActive(AlarmBit.OVERSPEED_WARNING), "warning should fire immediately");
        assertFalse(alarmState.isActive(AlarmBit.OVERSPEED), "full alarm needs duration");
    }

    @Test
    void overspeedAlarmSetAfterDurationThreshold() {
        // Tick at t=0
        eval(130, 0);
        // Tick at t=31 (threshold=30s)
        eval(130, 31);
        assertTrue(alarmState.isActive(AlarmBit.OVERSPEED));
        assertTrue(alarmState.isActive(AlarmBit.OVERSPEED_WARNING));
    }

    @Test
    void overspeedClearsWhenSpeedDrops() {
        eval(130, 0);
        eval(130, 35); // alarm set
        assertTrue(alarmState.isActive(AlarmBit.OVERSPEED));

        eval(80, 36); // back under limit
        assertFalse(alarmState.isActive(AlarmBit.OVERSPEED));
        assertFalse(alarmState.isActive(AlarmBit.OVERSPEED_WARNING));
    }

    @Test
    void overspeedBelowLimitNeverSetsAlarm() {
        eval(119, 0);
        eval(119, 60);
        assertFalse(alarmState.isActive(AlarmBit.OVERSPEED));
        assertFalse(alarmState.isActive(AlarmBit.OVERSPEED_WARNING));
    }

    // ── Fatigue ───────────────────────────────────────────────────────────────

    @Test
    void fatigueFlagsAfterContinuousDriveLimit() {
        // Drive for exactly contDriveLimitSeconds (4h = 14400s)
        eval(80, 0);
        eval(80, 14401);
        assertTrue(alarmState.isActive(AlarmBit.FATIGUE_DRIVING));
    }

    @Test
    void fatigueClearsAfterAdequateRest() {
        eval(80, 0);
        eval(80, 14401); // fatigue set
        assertTrue(alarmState.isActive(AlarmBit.FATIGUE_DRIVING));

        // Rest for minRestSeconds (20min = 1200s)
        eval(0, 14402);         // stop
        eval(0, 14402 + 1201);  // after 1201s rest
        assertFalse(alarmState.isActive(AlarmBit.FATIGUE_DRIVING));
    }

    @Test
    void fatigueNotSetBeforeLimitReached() {
        eval(80, 0);
        eval(80, 14399); // 1 second short of limit
        assertFalse(alarmState.isActive(AlarmBit.FATIGUE_DRIVING));
    }

    // ── Parking timeout ───────────────────────────────────────────────────────

    @Test
    void parkingTimeoutSetAfterMaxParkSeconds() {
        // maxParkSeconds = 3h = 10800s
        eval(0, 0);
        eval(0, 10801);
        assertTrue(alarmState.isActive(AlarmBit.TIMEOUT_PARKING));
    }

    @Test
    void parkingClearsWhenVehicleMovesAgain() {
        eval(0, 0);
        eval(0, 10801); // timeout set
        assertTrue(alarmState.isActive(AlarmBit.TIMEOUT_PARKING));

        eval(50, 10802); // moving again
        assertFalse(alarmState.isActive(AlarmBit.TIMEOUT_PARKING));
    }

    @Test
    void parkingNotSetBeforeThreshold() {
        eval(0, 0);
        eval(0, 10799);
        assertFalse(alarmState.isActive(AlarmBit.TIMEOUT_PARKING));
    }

    // ── AlarmState confirmAlarms ──────────────────────────────────────────────

    @Test
    void confirmAlarmsOnlyClearsOnAckBits() {
        alarmState.set(AlarmBit.EMERGENCY);       // clearOnAck = true  (bit 0)
        alarmState.set(AlarmBit.OVERSPEED);       // clearOnAck = false (bit 1)

        long mask = AlarmBit.EMERGENCY.mask() | AlarmBit.OVERSPEED.mask();
        alarmState.confirmAlarms(mask);

        assertFalse(alarmState.isActive(AlarmBit.EMERGENCY), "emergency should be cleared");
        assertTrue(alarmState.isActive(AlarmBit.OVERSPEED), "overspeed must not clear on ACK");
    }

    @Test
    void confirmAlarmsMaskDoesNotClearUnmatchedBits() {
        alarmState.set(AlarmBit.EMERGENCY);
        alarmState.set(AlarmBit.RISK_WARNING);

        alarmState.confirmAlarms(AlarmBit.EMERGENCY.mask()); // only confirm emergency

        assertFalse(alarmState.isActive(AlarmBit.EMERGENCY));
        assertTrue(alarmState.isActive(AlarmBit.RISK_WARNING));
    }

    // ── Alarm word encoding ───────────────────────────────────────────────────

    @Test
    void alarmWordReflectsActiveAlarms() {
        alarmState.set(AlarmBit.EMERGENCY);   // bit 0
        alarmState.set(AlarmBit.FATIGUE_DRIVING); // bit 14

        long word = alarmState.toAlarmWord();
        assertEquals(1L, word & 1L, "emergency bit");
        assertEquals(1L << 14, word & (1L << 14), "fatigue bit");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Evaluates at absolute epoch second t, building an Instant from offset. */
    private void eval(double speedKph, long epochOffset) {
        Instant t = Instant.ofEpochSecond(1_700_000_000L + epochOffset);
        engine.evaluate(speedKph, alarmState, params, t);
    }

    private static TerminalParams buildParams() {
        FleetConfig config = new FleetConfig();
        FleetConfig.ServerConfig server = new FleetConfig.ServerConfig();
        server.setHost("127.0.0.1");
        server.setPort(7611);
        config.setServer(server);
        FleetConfig.FleetSettings fleet = new FleetConfig.FleetSettings();
        fleet.setHeartbeatIntervalSeconds(30);
        fleet.setLocationIntervalSeconds(10);
        fleet.setAckTimeoutSeconds(30);
        config.setFleet(fleet);
        config.setJt1078(new FleetConfig.Jt1078Settings());
        return TerminalParams.from(config);
        // defaults: maxSpeedKph=120, overspeedDurationSeconds=30,
        //           contDriveLimitSeconds=14400, minRestSeconds=1200, maxParkSeconds=10800
    }
}
