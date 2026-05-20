package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/**
 * 0x0700 Tachograph (行驶记录仪) data upload (Table 71, JT808-2013).
 *
 * Sent in response to a 0x8700 platform tachograph command.
 * The commandType echoes the request; data is command-specific.
 *
 * The simulator returns a minimal synthetic payload for each command type.
 */
public class TachographDataUploadMessage extends AbstractJt808Message {

    private final int    commandType;
    private final byte[] data;

    public TachographDataUploadMessage(int sequence, String terminalId,
                                        int commandType, byte[] data) {
        super(sequence, terminalId, true);
        this.commandType = commandType;
        this.data        = data;
    }

    @Override public int messageId() { return MessageIds.TACHOGRAPH_DATA_UPLOAD; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(commandType);
        out.writeBytes(data);
    }
}
