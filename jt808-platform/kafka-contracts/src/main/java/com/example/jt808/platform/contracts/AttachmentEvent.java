package com.example.jt808.platform.contracts;

import java.time.Instant;

public record AttachmentEvent(
        String alarmId,
        String vehicleId,
        String terminalId,
        int alarmType,
        int channel,
        int format,
        int indexNum,
        String fileName,
        long size,
        String url,
        Instant uploadTime,
        Instant receivedAt
) {
}
