package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8800 Multimedia data upload response (Table 82, JT808-2013).
 *
 * Sent by the platform after receiving all (or some) sub-packages of a 0x0801 upload.
 * If resendPacketIds is empty, the full upload was received successfully.
 * Otherwise, the terminal must resend the listed sub-package IDs.
 */
public record MultimediaUploadAck(long multimediaId, List<Integer> resendPacketIds) {
    public boolean isComplete() { return resendPacketIds.isEmpty(); }
}
