package com.example.jt808sim.fleet.geofence;

/**
 * Decodes the 16-bit area attribute word from JT808-2013 Table 58.
 *
 * Used by circle, rectangle, and polygon area messages to control
 * time windows, speed limits, and alert behaviour.
 */
public record AreaAttribute(int value) {
    // Area has an active time window (start/end times present in the message)
    public boolean hasTimeWindow()        { return (value & 0x0001) != 0; }
    // Area has a speed limit (maxSpeed + overspeedDuration present)
    public boolean hasSpeedLimit()        { return (value & 0x0002) != 0; }
    public boolean alertDriverOnEntry()   { return (value & 0x0004) != 0; }
    public boolean alertPlatformOnEntry() { return (value & 0x0008) != 0; }
    public boolean alertDriverOnExit()    { return (value & 0x0010) != 0; }
    public boolean alertPlatformOnExit()  { return (value & 0x0020) != 0; }
    // Hemisphere of the area's coordinate points (applies to all lat/lon in the area item)
    public boolean southLatitude()        { return (value & 0x0040) != 0; }
    public boolean westLongitude()        { return (value & 0x0080) != 0; }
    public boolean doorOpenForbidden()    { return (value & 0x0100) != 0; }
    // Close comms module when entering the area
    public boolean closeCommOnEntry()     { return (value & 0x4000) != 0; }
    // Collect GNSS detailed location data on entry
    public boolean collectGnssOnEntry()   { return (value & 0x8000) != 0; }

    /** True if the platform should be notified on entry or exit. */
    public boolean shouldAlertPlatform() {
        return alertPlatformOnEntry() || alertPlatformOnExit();
    }
}
