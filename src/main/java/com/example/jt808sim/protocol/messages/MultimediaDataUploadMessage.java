package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;

/**
 * 0x0801 Multimedia data upload (Table 81, JT808-2013).
 *
 * Body layout (single-packet, no sub-packaging in Phase 5):
 *   DWORD    multimedia ID
 *   BYTE     multimedia type  (0=image 1=audio 2=video)
 *   BYTE     format code      (0=JPEG  1=TIF   2=MP3  3=WAV 4=WMV)
 *   BYTE     event item code
 *   BYTE     channel ID
 *   BYTE[28] location basic info (the 28-byte fixed part of 0x0200, no additional items)
 *   BYTE[n]  multimedia data packet (synthetic payload)
 *
 * The platform responds with 0x8800 multimedia data upload response.
 */
public class MultimediaDataUploadMessage extends AbstractJt808Message {
    private final long       multimediaId;
    private final int        mediaType;
    private final int        formatCode;
    private final int        eventCode;
    private final int        channelId;
    private final Coordinate coordinate;
    private final double     speedKph;
    private final int        heading;
    private final Instant    captureTime;
    private final VehicleState vehicleState;
    private final byte[]     payload;

    public MultimediaDataUploadMessage(int sequence, String terminalId,
                                        long multimediaId, int mediaType, int formatCode,
                                        int eventCode, int channelId,
                                        Coordinate coordinate, double speedKph, int heading,
                                        Instant captureTime, VehicleState vehicleState,
                                        byte[] payload) {
        super(sequence, terminalId, true);
        this.multimediaId  = multimediaId;
        this.mediaType     = mediaType;
        this.formatCode    = formatCode;
        this.eventCode     = eventCode;
        this.channelId     = channelId;
        this.coordinate    = coordinate;
        this.speedKph      = speedKph;
        this.heading       = heading;
        this.captureTime   = captureTime;
        this.vehicleState  = vehicleState;
        this.payload       = payload;
    }

    @Override public int messageId() { return MessageIds.MULTIMEDIA_DATA_UPLOAD; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeInt((int) multimediaId);
        out.writeByte(mediaType);
        out.writeByte(formatCode);
        out.writeByte(eventCode);
        out.writeByte(channelId);
        // Embedded 28-byte basic location info (Table 23) — no TLV additional info
        encodeBasicLocationInfo(out);
        // Multimedia data packet
        out.writeBytes(payload);
    }

    /** Writes the 28-byte fixed basic location info block (Table 23). */
    private void encodeBasicLocationInfo(ByteBuf out) {
        out.writeInt((int) vehicleState.alarmWord());
        out.writeInt((int) vehicleState.statusWord(coordinate, speedKph));
        out.writeInt((int) Math.round(Math.abs(coordinate.latitude())  * 1_000_000));
        out.writeInt((int) Math.round(Math.abs(coordinate.longitude()) * 1_000_000));
        out.writeShort(vehicleState.altitudeMeters());
        out.writeShort((int) Math.round(speedKph * 10));
        out.writeShort(Math.floorMod(heading, 360));
        com.example.jt808sim.protocol.Jt808CodecSupport.writeBcdTimestamp(out, captureTime);
    }
}
