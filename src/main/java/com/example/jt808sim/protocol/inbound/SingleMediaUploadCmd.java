package com.example.jt808sim.protocol.inbound;

/**
 * 0x8805 Single storage multimedia data retrieval uploads command (Table 90, JT808-2013).
 * Requests upload of a specific multimedia item by ID.
 * deleteSign: 0=keep, 1=delete after upload.
 */
public record SingleMediaUploadCmd(long multimediaId, int deleteSign) {
}
