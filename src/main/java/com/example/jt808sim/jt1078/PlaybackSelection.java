package com.example.jt808sim.jt1078;

import java.time.Instant;

public record PlaybackSelection(
        RecordedMediaResource resource,
        Instant effectiveStartTime,
        Instant effectiveEndTime) {
}
