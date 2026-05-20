package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/**
 * 0x0800 Multimedia event information uploading (Table 80, JT808-2013).
 * Fired immediately when the terminal captures media in response to an event.
 * Requires 0x8001 platform response.
 *
 * mediaType:  0=image  1=audio  2=video
 * formatCode: 0=JPEG   1=TIF    2=MP3   3=WAV   4=WMV
 * eventCode:  0=platform cmd  1=timing  2=robbery  3=collision/rollover
 */
public class MultimediaEventMessage extends AbstractJt808Message {
    private final long multimediaId;
    private final int  mediaType;
    private final int  formatCode;
    private final int  eventCode;
    private final int  channelId;

    public MultimediaEventMessage(int sequence, String terminalId,
                                   long multimediaId, int mediaType, int formatCode,
                                   int eventCode, int channelId) {
        super(sequence, terminalId, true);
        this.multimediaId = multimediaId;
        this.mediaType    = mediaType;
        this.formatCode   = formatCode;
        this.eventCode    = eventCode;
        this.channelId    = channelId;
    }

    @Override public int messageId() { return MessageIds.MULTIMEDIA_EVENT; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeInt((int) multimediaId);
        out.writeByte(mediaType);
        out.writeByte(formatCode);
        out.writeByte(eventCode);
        out.writeByte(channelId);
    }
}
