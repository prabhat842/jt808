package com.example.jt808sim.fleet;

import com.example.jt808sim.physics.Coordinate;

/**
 * Real-time vehicle state encoded into every 0x0200 location report.
 *
 * Alarm word (Table 24) starts at zero; Phase 2 introduces the alarm state machine.
 * Status word (Table 25) is derived from live position and speed.
 * Additional info items (Table 27) are maintained here and emitted with each report.
 */
public class VehicleState {

    // Status word bits (Table 25)
    private static final long BIT_ACC         = 1L;        // bit 0: ACC on
    private static final long BIT_POSITIONING = 1L << 1;   // bit 1: positioning valid
    private static final long BIT_SOUTH_LAT   = 1L << 2;   // bit 2: south latitude
    private static final long BIT_WEST_LON    = 1L << 3;   // bit 3: west longitude
    private static final long BIT_RUNNING     = 1L << 4;   // bit 4: running (vs stopped)
    private static final long BIT_GPS         = 1L << 18;  // bit 18: GPS positioning

    // Alarm word — updated from AlarmState before each location report
    private volatile long alarmWord = 0L;

    // Odometer accumulated in 1/10 km (Table 27 item 0x01, DWORD)
    private volatile long odometerTenthKm = 0L;

    // Fuel level in 1/10 L (Table 27 item 0x02, WORD); starts at 80L = 800
    private volatile int fuelTenthLiters = 800;

    // Wireless signal strength 0-100 (Table 27 item 0x30, BYTE)
    private volatile int signalStrength = 85;

    // GNSS satellite count (Table 27 item 0x31, BYTE)
    private volatile int satelliteCount = 12;

    // Altitude in meters (Table 23, WORD); synthetic default for flat-land routes
    private volatile int altitudeMeters = 50;

    public long alarmWord() {
        return alarmWord;
    }

    public void setAlarmWord(long alarmWord) {
        this.alarmWord = alarmWord;
    }

    public long odometerTenthKm() {
        return odometerTenthKm;
    }

    public void addDistanceMeters(double meters) {
        odometerTenthKm += Math.round(meters / 100.0);
    }

    public int fuelTenthLiters() {
        return fuelTenthLiters;
    }

    public int signalStrength() {
        return signalStrength;
    }

    public int satelliteCount() {
        return satelliteCount;
    }

    public int altitudeMeters() {
        return altitudeMeters;
    }

    /**
     * Derives the 32-bit status word from current coordinate and speed (Table 25).
     * South/west hemisphere bits are computed from the coordinate sign so the
     * encoded lat/lon can always be written as absolute values.
     */
    public long statusWord(Coordinate coordinate, double speedKph) {
        long status = BIT_POSITIONING | BIT_GPS;
        if (speedKph > 0) {
            status |= BIT_ACC | BIT_RUNNING;
        }
        if (coordinate.latitude() < 0)  status |= BIT_SOUTH_LAT;
        if (coordinate.longitude() < 0) status |= BIT_WEST_LON;
        return status;
    }
}
