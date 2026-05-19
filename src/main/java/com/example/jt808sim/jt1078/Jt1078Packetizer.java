package com.example.jt808sim.jt1078;

import java.util.ArrayList;
import java.util.List;

public final class Jt1078Packetizer {
    private Jt1078Packetizer() {
    }

    public static List<Jt1078MediaPacket> packetize(String terminalId, int channel, long sequenceBase, Jt1078Frame frame, int maxPayloadBytes) {
        byte[] payload = frame.payload();
        int chunkSize = Math.max(1, maxPayloadBytes);
        if (payload.length <= chunkSize) {
            return List.of(new Jt1078MediaPacket(
                    terminalId,
                    channel,
                    sequenceBase,
                    frame,
                    Jt1078MediaPacket.Subpackage.ATOMIC,
                    payload));
        }

        List<Jt1078MediaPacket> packets = new ArrayList<>();
        int offset = 0;
        int chunkIndex = 0;
        while (offset < payload.length) {
            int length = Math.min(chunkSize, payload.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(payload, offset, chunk, 0, length);
            packets.add(new Jt1078MediaPacket(
                    terminalId,
                    channel,
                    sequenceBase + chunkIndex,
                    frame,
                    subpackageFor(chunkIndex, offset + length >= payload.length),
                    chunk));
            offset += length;
            chunkIndex++;
        }
        return packets;
    }

    private static Jt1078MediaPacket.Subpackage subpackageFor(int chunkIndex, boolean isLast) {
        if (chunkIndex == 0) {
            return isLast ? Jt1078MediaPacket.Subpackage.ATOMIC : Jt1078MediaPacket.Subpackage.FIRST;
        }
        return isLast ? Jt1078MediaPacket.Subpackage.LAST : Jt1078MediaPacket.Subpackage.MIDDLE;
    }
}
