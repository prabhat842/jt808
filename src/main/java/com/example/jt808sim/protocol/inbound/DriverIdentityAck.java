package com.example.jt808sim.protocol.inbound;

import java.time.Instant;

/**
 * 0x8702 Platform acknowledgement of driver identity report (Table 74, JT808-2013).
 *
 * Sent by the platform in reply to 0x0702.
 * platformTime is the platform's current timestamp — the terminal uses it to
 * synchronize its clock (real hardware would set its RTC from this).
 */
public record DriverIdentityAck(int responseSerial, Instant platformTime) {
}
