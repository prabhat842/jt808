package com.example.jt808sim.fleet;

/**
 * Per-terminal alarm state — tracks the 32 alarm bits from JT808-2013 Table 24.
 *
 * All methods run on the channel's single Netty I/O thread, so no synchronisation
 * is required. The alarm word is updated here and copied into {@link VehicleState}
 * before each location report is encoded.
 */
public class AlarmState {

    private long activeWord = 0L;

    /** Activates the given alarm bit. Idempotent. */
    public void set(AlarmBit alarm) {
        activeWord |= alarm.mask();
    }

    /** Deactivates the given alarm bit. Idempotent. */
    public void clear(AlarmBit alarm) {
        activeWord &= ~alarm.mask();
    }

    public boolean isActive(AlarmBit alarm) {
        return (activeWord & alarm.mask()) != 0;
    }

    /** Returns the full 32-bit alarm word for embedding in 0x0200 basic info. */
    public long toAlarmWord() {
        return activeWord;
    }

    /**
     * Handles a 0x8203 manual alarm confirm message from the platform.
     *
     * Only bits whose {@link AlarmBit#clearOnAck} flag is true are cleared.
     * The alarmTypeMask bits (Table 36) map 1:1 to the alarm word bits.
     */
    public void confirmAlarms(long alarmTypeMask) {
        for (AlarmBit alarm : AlarmBit.VALUES) {
            if (alarm.clearOnAck && (alarmTypeMask & alarm.mask()) != 0) {
                clear(alarm);
            }
        }
    }

    /**
     * Clears all ON_ACK alarm bits (those that clear when the platform ACKs a
     * location report). Called when 0x8001 is received for a 0x0200 message.
     */
    public void clearOnAckBits() {
        for (AlarmBit alarm : AlarmBit.VALUES) {
            if (alarm.clearOnAck) clear(alarm);
        }
    }
}
