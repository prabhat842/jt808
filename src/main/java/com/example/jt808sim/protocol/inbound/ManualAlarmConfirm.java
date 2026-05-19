package com.example.jt808sim.protocol.inbound;

/**
 * 0x8203 Manually confirm alarm message (Table 35-36, JT808-2013).
 *
 * serialNumber == 0 means confirm all messages of the specified alarm type(s).
 * alarmTypeMask is a bitmask (Table 36) indicating which alarm types to confirm.
 */
public record ManualAlarmConfirm(int serialNumber, long alarmTypeMask) {
}
