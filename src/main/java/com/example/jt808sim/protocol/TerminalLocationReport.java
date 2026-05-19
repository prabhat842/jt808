package com.example.jt808sim.protocol;

import java.time.Instant;

public record TerminalLocationReport(
        long warnBit,
        long stateBit,
        double latitude,
        double longitude,
        int altitudeMeters,
        double speedKph,
        int direction,
        Instant gpsTime
) {
    public boolean positioned() {
        return (stateBit & 0x00000002L) != 0;
    }
}
