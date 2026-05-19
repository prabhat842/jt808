package com.example.jt808.platform.contracts;

import java.time.Instant;

public record HeartbeatEvent(
        String vehicleId,
        String terminalId,
        int sequence,
        String remoteAddress,
        Instant receivedAt
) {
}
