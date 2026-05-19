package com.example.jt808.platform.contracts;

import java.time.Instant;

public record MediaSignalEvent(
        String vehicleId,
        String terminalId,
        int messageId,
        int sequence,
        String direction,
        Instant occurredAt
) {
}
