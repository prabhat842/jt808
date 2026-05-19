package com.example.jt808.platform.contracts;

import java.time.Instant;

public record GpsTelemetryEvent(
        String vehicleId,
        String deviceId,
        String terminalId,
        Instant gpsTime,
        double latitude,
        double longitude,
        int altitudeMeters,
        double speedKph,
        int direction,
        long stateBit,
        long warnBit,
        boolean positioned,
        String remoteAddress,
        Instant receivedAt
) {
}
