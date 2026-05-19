package com.example.jt808.platform.gateway;

import java.time.Instant;

record CommandCorrelation(
        String commandId,
        String terminalId,
        int messageId,
        int sequence,
        String source,
        Instant createdAt
) {
}
