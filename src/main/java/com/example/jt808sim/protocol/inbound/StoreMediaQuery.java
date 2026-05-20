package com.example.jt808sim.protocol.inbound;

import java.time.Instant;

/**
 * 0x8802 Retrieve of store multimedia data (Table 85, JT808-2013).
 *
 * mediaType: 0=image, 1=audio, 2=video
 * channelId: 0=all channels
 * eventCode: 0=platform command, 1=timing, 2=robbery, 3=collision/rollover; others reserved
 * startTime/endTime: null if set to all-zeros (no time constraint)
 */
public record StoreMediaQuery(int mediaType, int channelId, int eventCode,
                               Instant startTime, Instant endTime) {
}
