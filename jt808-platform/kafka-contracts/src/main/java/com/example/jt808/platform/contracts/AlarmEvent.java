package com.example.jt808.platform.contracts;

import java.time.Instant;

public record AlarmEvent(
        String alarmId,
        String vehicleId,
        String terminalId,
        int alarmType,
        int alarmLevel,
        long warnBit,
        double latitude,
        double longitude,
        Instant alarmTime,
        Instant receivedAt
) {
}
