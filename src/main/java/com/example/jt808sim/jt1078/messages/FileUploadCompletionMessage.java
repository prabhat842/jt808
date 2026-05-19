package com.example.jt808sim.jt1078.messages;

import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.messages.AbstractJt808Message;
import io.netty.buffer.ByteBuf;

public class FileUploadCompletionMessage extends AbstractJt808Message {
    private final int responseSequence;
    private final int result;

    public FileUploadCompletionMessage(int sequence, String terminalId, int responseSequence, int result) {
        super(sequence, terminalId, true);
        this.responseSequence = responseSequence;
        this.result = result;
    }

    @Override
    public int messageId() {
        return MessageIds.JT1078_FILE_UPLOAD_COMPLETION;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSequence);
        out.writeByte(result);
    }
}
