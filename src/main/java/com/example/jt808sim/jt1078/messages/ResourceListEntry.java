package com.example.jt808sim.jt1078.messages;

import java.time.Instant;

public record ResourceListEntry(
        int channel,
        Instant startTime,
        Instant endTime,
        long alarmFlagsHigh,
        long alarmFlagsLow,
        int audioVideoType,
        int streamType,
        int memoryType,
        long fileSizeBytes) {
}
