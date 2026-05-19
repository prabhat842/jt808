package com.example.jt808sim.jt1078.messages;

import com.example.jt808sim.jt1078.Jt1078MediaConfig;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.messages.AbstractJt808Message;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class AudioVideoAttributesMessage extends AbstractJt808Message {
    private final Jt1078MediaConfig mediaConfig;
    private final List<Integer> mediaChannels;

    public AudioVideoAttributesMessage(int sequence, String terminalId, Jt1078MediaConfig mediaConfig, List<Integer> mediaChannels) {
        super(sequence, terminalId, true);
        this.mediaConfig = mediaConfig;
        this.mediaChannels = mediaChannels == null ? List.of() : List.copyOf(mediaChannels);
    }

    @Override
    public int messageId() {
        return MessageIds.JT1078_UPLOAD_AV_ATTRIBUTES;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(19); // AAC
        out.writeByte(mediaConfig.capture().audioEnabled() ? Math.max(1, mediaConfig.capture().audioChannels()) : 0);
        out.writeByte(audioSampleRateCode(mediaConfig.capture().audioSampleRate()));
        out.writeByte(audioSampleBitsCode());
        out.writeShort(audioFrameLength(mediaConfig.capture().audioSampleRate()));
        out.writeByte(mediaConfig.talk().enabled() ? 1 : 0);
        out.writeByte(98); // H.264
        out.writeByte(Math.max(1, mediaChannels.size()));
        out.writeByte(Math.max(1, mediaChannels.size()));
    }

    private static int audioSampleRateCode(int sampleRate) {
        if (sampleRate >= 48000) {
            return 3;
        }
        if (sampleRate >= 44100) {
            return 2;
        }
        if (sampleRate >= 22050) {
            return 1;
        }
        return 0;
    }

    private static int audioSampleBitsCode() {
        return 1; // 16-bit
    }

    private static int audioFrameLength(int sampleRate) {
        return sampleRate <= 8000 ? 160 : 1024;
    }
}
