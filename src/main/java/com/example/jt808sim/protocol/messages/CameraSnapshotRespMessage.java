package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 0x0805 Camera immediately taken command response (Table 84, JT808-2013).
 *
 * result: 0=success, 1=failure, 2=channel not supported
 * mediaIds: list of multimedia IDs for successfully taken photos (valid when result==0)
 */
public class CameraSnapshotRespMessage extends AbstractJt808Message {
    private final int responseSerial;
    private final int result;
    private final List<Long> mediaIds;

    public CameraSnapshotRespMessage(int sequence, String terminalId,
                                      int responseSerial, int result, List<Long> mediaIds) {
        super(sequence, terminalId, true);
        this.responseSerial = responseSerial;
        this.result         = result;
        this.mediaIds       = mediaIds;
    }

    @Override public int messageId() { return MessageIds.CAMERA_SNAPSHOT_RESP; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSerial);
        out.writeByte(result);
        out.writeShort(mediaIds.size());
        for (long id : mediaIds) {
            out.writeInt((int) id);
        }
    }
}
