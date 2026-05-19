package com.example.jt808sim.fleet;

/**
 * All 32 alarm bits defined in JT808-2013 Table 24, with their clear rule.
 *
 * clearOnAck == true  → bit is zeroed after the platform sends 0x8001 (general ACK)
 * clearOnAck == false → bit stays set until the underlying condition is relieved
 */
public enum AlarmBit {
    EMERGENCY               ( 0, true,  "Emergency alarm"),
    OVERSPEED               ( 1, false, "Overspeed"),
    DRIVING_MALFUNCTION     ( 2, false, "Driving alarm malfunction"),
    RISK_WARNING            ( 3, true,  "Risk warning"),
    GNSS_MODULE_FAULT       ( 4, false, "GNSS module malfunction"),
    GNSS_ANTENNA_DISCONNECT ( 5, false, "GNSS antenna not connected or cut"),
    GNSS_ANTENNA_SHORT      ( 6, false, "GNSS antenna short circuit"),
    MAIN_POWER_UNDERVOLTAGE ( 7, false, "Main power undervoltage"),
    MAIN_POWER_OFF          ( 8, false, "Main power turned off"),
    LCD_MALFUNCTION         ( 9, false, "Terminal LCD/display malfunction"),
    TTS_MALFUNCTION         (10, false, "TTS module malfunction"),
    CAMERA_MALFUNCTION      (11, false, "Camera malfunction"),
    IC_CARD_MALFUNCTION     (12, false, "Road transport IC card module malfunction"),
    OVERSPEED_WARNING       (13, false, "Overspeed warning"),
    FATIGUE_DRIVING         (14, false, "Fatigue driving warning"),
    // bits 15-17 reserved
    ACCUMULATED_OVERSPEED   (18, false, "Accumulated overspeed driving time"),
    TIMEOUT_PARKING         (19, false, "Timeout parking"),
    AREA_ENTRY_EXIT         (20, true,  "Enter/exit area"),
    ROUTE_ENTRY_EXIT        (21, true,  "Enter/exit route"),
    ROUTE_TIME_ALARM        (22, true,  "Route driving time insufficient or too long"),
    OFF_TRACK               (23, false, "Off track"),
    VSS_MALFUNCTION         (24, false, "Vehicle VSS malfunction"),
    ABNORMAL_FUEL           (25, false, "Abnormal fuel capacity"),
    VEHICLE_STOLEN          (26, false, "Vehicle stolen"),
    ILLEGAL_IGNITION        (27, true,  "Illegal vehicle ignition"),
    ILLEGAL_DISPLACEMENT    (28, true,  "Illegal vehicle displacement"),
    COLLISION_WARNING       (29, false, "Collision warning"),
    ROLLOVER_WARNING        (30, false, "Rollover warning"),
    ILLEGAL_DOOR_OPEN       (31, true,  "Illegal door open");

    public final int     bit;
    public final boolean clearOnAck;
    public final String  name;

    AlarmBit(int bit, boolean clearOnAck, String name) {
        this.bit        = bit;
        this.clearOnAck = clearOnAck;
        this.name       = name;
    }

    public long mask() { return 1L << bit; }

    public static final AlarmBit[] VALUES = values();
}
