package com.example.jt808.platform.contracts;

import java.time.Instant;

public record CommandResponseEvent(
        String vehicleId,
        String terminalId,
        int sequence,
        int responseSequence,
        int responseMessageId,
        int result,
        Instant receivedAt
) {
}
