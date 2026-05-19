package com.example.jt808.platform.gateway;

import java.time.Instant;

record TerminalSession(
        String terminalId,
        String remoteAddress,
        boolean authenticated,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant updatedAt
) {
    TerminalSession authenticated(Instant now) {
        return new TerminalSession(terminalId, remoteAddress, true, registeredAt, lastHeartbeatAt, now);
    }

    TerminalSession heartbeat(Instant now) {
        return new TerminalSession(terminalId, remoteAddress, authenticated, registeredAt, now, now);
    }
}
