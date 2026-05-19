package com.example.jt808sim.fleet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Evaluates alarm conditions on each location tick and drives {@link AlarmState}.
 *
 * Phase 2 implements the physically computable alarms:
 *
 *   Bit  1  OVERSPEED          — speed > maxSpeedKph for ≥ overspeedDurationSeconds
 *   Bit 13  OVERSPEED_WARNING  — speed > maxSpeedKph (fires immediately, clears with overspeed)
 *   Bit 14  FATIGUE_DRIVING    — continuous driving ≥ contDriveLimitSeconds without adequate rest
 *   Bit 18  ACCUMULATED_OVERSPEED — total overspeed seconds today ≥ accumulated threshold (30 min)
 *   Bit 19  TIMEOUT_PARKING    — stopped for ≥ maxParkSeconds
 *
 * Phase 3 will add geofence-triggered bits (20, 21, 22, 23).
 * Fault/hardware bits (2,4-12,24-26) are injectable via future test APIs.
 */
public class AlarmEngine {
    private static final Logger log = LoggerFactory.getLogger(AlarmEngine.class);

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long ACCUMULATED_OVERSPEED_THRESHOLD_SECONDS = 30 * 60L; // 30 minutes

    private final String terminalId;

    // Overspeed tracking
    private Instant overspeedStart;
    private long overspeedSecondsToday;
    private LocalDate overspeedResetDate;

    // Fatigue tracking
    private Instant continuousDriveStart;
    private Instant lastRestStart;

    // Parking timeout tracking
    private Instant parkingStart;

    // Time of last evaluation (to compute accumulated intervals)
    private Instant lastEvalTime;

    public AlarmEngine(String terminalId) {
        this.terminalId = terminalId;
    }

    /**
     * Called once per location report tick.
     *
     * @param speedKph    current speed from the trajectory snapshot
     * @param alarmState  mutable alarm state to be updated
     * @param params      live terminal parameters (thresholds)
     * @param now         wall-clock time of this evaluation
     */
    public void evaluate(double speedKph, AlarmState alarmState, TerminalParams params, Instant now) {
        long intervalSeconds = lastEvalTime == null ? 0
                : Math.max(0, now.getEpochSecond() - lastEvalTime.getEpochSecond());
        lastEvalTime = now;

        evaluateOverspeed(speedKph, intervalSeconds, alarmState, params, now);
        evaluateFatigue(speedKph, alarmState, params, now);
        evaluateParking(speedKph, alarmState, params, now);
    }

    // ── Overspeed (bits 1, 13, 18) ───────────────────────────────────────────

    private void evaluateOverspeed(double speedKph, long intervalSeconds,
                                   AlarmState state, TerminalParams params, Instant now) {
        long maxSpeed = params.maxSpeedKph();

        // Reset accumulated counter at midnight (CN time)
        LocalDate today = now.atZone(CN_ZONE).toLocalDate();
        if (overspeedResetDate == null || !today.equals(overspeedResetDate)) {
            overspeedSecondsToday = 0;
            overspeedResetDate = today;
        }

        if (speedKph > maxSpeed) {
            if (overspeedStart == null) {
                overspeedStart = now;
                log.debug("terminal {} overspeed started {}kph limit {}kph",
                        terminalId, speedKph, maxSpeed);
            }
            // Overspeed warning fires immediately (bit 13)
            state.set(AlarmBit.OVERSPEED_WARNING);

            // Accumulate time spent over speed limit
            overspeedSecondsToday += intervalSeconds;

            // Full alarm activates after duration threshold (bit 1)
            long durationSecs = now.getEpochSecond() - overspeedStart.getEpochSecond();
            if (durationSecs >= params.overspeedDurationSeconds()) {
                if (!state.isActive(AlarmBit.OVERSPEED)) {
                    log.info("terminal {} OVERSPEED alarm (bit 1) set after {}s at {}kph",
                            terminalId, durationSecs, speedKph);
                }
                state.set(AlarmBit.OVERSPEED);
            }
        } else {
            if (overspeedStart != null) {
                log.debug("terminal {} overspeed cleared", terminalId);
                state.clear(AlarmBit.OVERSPEED);
                state.clear(AlarmBit.OVERSPEED_WARNING);
                overspeedStart = null;
            }
        }

        // Accumulated overspeed for the day (bit 18)
        if (overspeedSecondsToday >= ACCUMULATED_OVERSPEED_THRESHOLD_SECONDS) {
            if (!state.isActive(AlarmBit.ACCUMULATED_OVERSPEED)) {
                log.info("terminal {} ACCUMULATED_OVERSPEED alarm (bit 18) set ({}min today)",
                        terminalId, overspeedSecondsToday / 60);
            }
            state.set(AlarmBit.ACCUMULATED_OVERSPEED);
        } else if (speedKph <= maxSpeed) {
            state.clear(AlarmBit.ACCUMULATED_OVERSPEED);
        }
    }

    // ── Fatigue driving (bit 14) ─────────────────────────────────────────────

    private void evaluateFatigue(double speedKph, AlarmState state, TerminalParams params, Instant now) {
        if (speedKph > 0) {
            if (continuousDriveStart == null) {
                continuousDriveStart = now;
            }
            lastRestStart = null; // vehicle is moving; reset rest timer

            long driveSecs = now.getEpochSecond() - continuousDriveStart.getEpochSecond();
            if (driveSecs >= params.contDriveLimitSeconds() && !state.isActive(AlarmBit.FATIGUE_DRIVING)) {
                log.info("terminal {} FATIGUE_DRIVING alarm (bit 14) set after {}h continuous drive",
                        terminalId, driveSecs / 3600);
                state.set(AlarmBit.FATIGUE_DRIVING);
            }
        } else {
            if (lastRestStart == null) {
                lastRestStart = now;
            }
            long restSecs = now.getEpochSecond() - lastRestStart.getEpochSecond();
            if (state.isActive(AlarmBit.FATIGUE_DRIVING) && restSecs >= params.minRestSeconds()) {
                log.info("terminal {} FATIGUE_DRIVING cleared after {}min rest",
                        terminalId, restSecs / 60);
                state.clear(AlarmBit.FATIGUE_DRIVING);
                continuousDriveStart = null; // restart drive timer after adequate rest
            }
        }
    }

    // ── Timeout parking (bit 19) ─────────────────────────────────────────────

    private void evaluateParking(double speedKph, AlarmState state, TerminalParams params, Instant now) {
        if (speedKph == 0) {
            if (parkingStart == null) {
                parkingStart = now;
            }
            long parkSecs = now.getEpochSecond() - parkingStart.getEpochSecond();
            if (parkSecs >= params.maxParkSeconds() && !state.isActive(AlarmBit.TIMEOUT_PARKING)) {
                log.info("terminal {} TIMEOUT_PARKING alarm (bit 19) set after {}h",
                        terminalId, parkSecs / 3600);
                state.set(AlarmBit.TIMEOUT_PARKING);
            }
        } else {
            if (parkingStart != null) {
                state.clear(AlarmBit.TIMEOUT_PARKING);
                parkingStart = null;
            }
        }
    }
}
