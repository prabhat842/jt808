package com.example.jt808sim.jt1078.messages;

import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.messages.AbstractJt808Message;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class ResourceListUploadMessage extends AbstractJt808Message {
    private final int responseSequence;
    private final List<ResourceListEntry> entries;

    public ResourceListUploadMessage(int sequence, String terminalId, int responseSequence, List<ResourceListEntry> entries) {
        super(sequence, terminalId, true);
        this.responseSequence = responseSequence;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public int messageId() {
        return MessageIds.JT1078_UPLOAD_RESOURCE_LIST;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSequence);
        out.writeInt(entries.size());
        for (ResourceListEntry entry : entries) {
            out.writeByte(entry.channel());
            Jt808CodecSupport.writeBcdTimestamp(out, entry.startTime());
            Jt808CodecSupport.writeBcdTimestamp(out, entry.endTime());
            out.writeInt((int) entry.alarmFlagsHigh());
            out.writeInt((int) entry.alarmFlagsLow());
            out.writeByte(entry.audioVideoType());
            out.writeByte(entry.streamType());
            out.writeByte(entry.memoryType());
            out.writeInt((int) entry.fileSizeBytes());
        }
    }
}
