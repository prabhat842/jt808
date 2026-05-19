package com.example.jt808sim.protocol.inbound;

/**
 * 0x8202 Temporary location tracking control (Table 34, JT808-2013).
 *
 * intervalSeconds == 0 means stop tracking and revert to normal report rate.
 * validitySeconds is the duration for which the accelerated interval applies.
 */
public record TempLocationTracking(int intervalSeconds, long validitySeconds) {
}
