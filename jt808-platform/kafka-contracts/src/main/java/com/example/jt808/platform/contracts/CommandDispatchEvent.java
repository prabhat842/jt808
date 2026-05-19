package com.example.jt808.platform.contracts;

import java.time.Instant;

public record CommandDispatchEvent(
        String commandId,
        String vehicleId,
        String terminalId,
        int messageId,
        int sequence,
        String source,
        Instant dispatchedAt
) {
}
