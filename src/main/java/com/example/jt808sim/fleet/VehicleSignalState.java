package com.example.jt808sim.fleet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Computes the vehicle signal status word for additional info item 0x25 (Table 31).
 *
 * Signal values are derived from live vehicle motion data:
 *   - Low beam: on during simulated night hours (18:00–06:00 Beijing time)
 *   - High beam: off (not simulated)
 *   - Right/left indicator: fired when heading changes by > 10° since last tick
 *   - Brake: on when the vehicle is decelerating noticeably or has just stopped
 *   - Air conditioner: on during summer months (May–September)
 *   - Other signals: off by default, injectable for fault simulation
 *
 * The 32-bit word layout follows Table 31:
 *   bit 0  = low beam
 *   bit 1  = high beam
 *   bit 2  = right indicator
 *   bit 3  = left indicator
 *   bit 4  = brake
 *   bit 5  = reverse
 *   bit 6  = fog light
 *   bit 7  = outline marker lamps
 *   bit 8  = horn
 *   bit 9  = air conditioner
 *   bit 10 = neutral gear
 *   bit 11 = retarder operation
 *   bit 12 = ABS operation
 *   bit 13 = heater operation
 *   bit 14 = clutch status
 */
public class VehicleSignalState {
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    private double prevSpeedKph = -1;   // -1 = uninitialised (first tick)
    private int    prevHeading  = -1;

    // Computed this tick
    private boolean lowBeam;
    private boolean rightIndicator;
    private boolean leftIndicator;
    private boolean brake;
    private boolean airConditioner;

    /**
     * Updates all signals for the current location tick.
     *
     * @param speedKph current speed from trajectory snapshot
     * @param heading  current heading 0-359°
     * @param now      wall-clock time for day/night and seasonal signals
     */
    public void update(double speedKph, int heading, Instant now) {
        ZonedDateTime dt = now.atZone(CN_ZONE);
        int hour  = dt.getHour();
        int month = dt.getMonthValue();

        // Low beam: night driving simulation
        lowBeam = hour >= 18 || hour < 6;

        // Air conditioner: summer months during daytime
        airConditioner = month >= 5 && month <= 9 && hour >= 8 && hour < 20;

        // Brake: speed dropped by more than 3 km/h since last tick, or vehicle just stopped
        if (prevSpeedKph >= 0) {
            brake = (prevSpeedKph - speedKph) > 3.0;
        }

        // Indicators: heading change direction (skip first tick)
        if (prevHeading >= 0) {
            int diff = ((heading - prevHeading + 540) % 360) - 180; // -180 to +180
            rightIndicator = diff > 10;
            leftIndicator  = diff < -10;
        }

        prevSpeedKph = speedKph;
        prevHeading  = heading;
    }

    /**
     * Returns the 32-bit vehicle signal status word for additional info item 0x25.
     */
    public int toSignalWord() {
        int word = 0;
        if (lowBeam)        word |= (1 << 0);
        // bit 1 (high beam): 0
        if (rightIndicator) word |= (1 << 2);
        if (leftIndicator)  word |= (1 << 3);
        if (brake)          word |= (1 << 4);
        // bit 5 (reverse): 0
        // bit 6 (fog): 0
        if (lowBeam)        word |= (1 << 7); // outline markers on when lights on
        // bits 8-9: horn, air conditioner
        if (airConditioner) word |= (1 << 9);
        // bits 10-14: neutral, retarder, ABS, heater, clutch — all 0 in base sim
        return word;
    }
}
