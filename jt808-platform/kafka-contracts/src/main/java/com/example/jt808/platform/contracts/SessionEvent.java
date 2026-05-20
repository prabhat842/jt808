package com.example.jt808.platform.contracts;

import java.time.Instant;

/**
 * Published to {@code telemetry.session} on terminal lifecycle transitions.
 * event: "ONLINE" when terminal registers, "AUTHENTICATED" on auth, "OFFLINE" on disconnect.
 */
public record SessionEvent(
        String  vehicleId,
        String  terminalId,
        String  event,
        String  remoteAddress,
        Instant occurredAt
) {
}
