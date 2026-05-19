package com.example.jt808.platform.gateway;

/**
 * Metadata for the 32 alarm bits from JT808-2013 Table 24.
 * Used to build human-readable AlarmEvent records.
 */
final class AlarmBitMeta {

    record Meta(String name, int level) {}  // level: 1=warning 2=alarm 3=critical

    private static final Meta[] BITS = new Meta[32];

    static {
        BITS[ 0] = new Meta("Emergency alarm",                    3);
        BITS[ 1] = new Meta("Overspeed",                          2);
        BITS[ 2] = new Meta("Driving alarm malfunction",          2);
        BITS[ 3] = new Meta("Risk warning",                       1);
        BITS[ 4] = new Meta("GNSS module malfunction",            2);
        BITS[ 5] = new Meta("GNSS antenna not connected or cut",  2);
        BITS[ 6] = new Meta("GNSS antenna short circuit",         2);
        BITS[ 7] = new Meta("Main power undervoltage",            2);
        BITS[ 8] = new Meta("Main power turned off",              3);
        BITS[ 9] = new Meta("Terminal LCD malfunction",           1);
        BITS[10] = new Meta("TTS module malfunction",             1);
        BITS[11] = new Meta("Camera malfunction",                 1);
        BITS[12] = new Meta("IC card module malfunction",         1);
        BITS[13] = new Meta("Overspeed warning",                  1);
        BITS[14] = new Meta("Fatigue driving warning",            2);
        BITS[18] = new Meta("Accumulated overspeed driving time", 2);
        BITS[19] = new Meta("Timeout parking",                    1);
        BITS[20] = new Meta("Area entry/exit alarm",              2);
        BITS[21] = new Meta("Route entry/exit alarm",             2);
        BITS[22] = new Meta("Route driving time alarm",           2);
        BITS[23] = new Meta("Off track",                          2);
        BITS[24] = new Meta("VSS malfunction",                    1);
        BITS[25] = new Meta("Abnormal fuel capacity",             2);
        BITS[26] = new Meta("Vehicle stolen",                     3);
        BITS[27] = new Meta("Illegal vehicle ignition",           2);
        BITS[28] = new Meta("Illegal vehicle displacement",       2);
        BITS[29] = new Meta("Collision warning",                  3);
        BITS[30] = new Meta("Rollover warning",                   3);
        BITS[31] = new Meta("Illegal door open",                  2);
    }

    static String name(int bit) {
        Meta m = bit >= 0 && bit < 32 ? BITS[bit] : null;
        return m != null ? m.name() : "Unknown alarm bit " + bit;
    }

    static int level(int bit) {
        Meta m = bit >= 0 && bit < 32 ? BITS[bit] : null;
        return m != null ? m.level() : 1;
    }

    private AlarmBitMeta() {}
}
