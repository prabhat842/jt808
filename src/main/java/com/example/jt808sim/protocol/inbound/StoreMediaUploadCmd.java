package com.example.jt808sim.protocol.inbound;

import java.time.Instant;

/**
 * 0x8803 Store multimedia data upload command (Table 88, JT808-2013).
 * Terminal ACKs with 0x0001 then uploads matching items via 0x0801.
 * deleteAfterUpload: 1=delete the local copy after successful upload.
 */
public record StoreMediaUploadCmd(int mediaType, int channelId, int eventCode,
                                   Instant startTime, Instant endTime,
                                   int deleteAfterUpload) {
}
