package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.Jt808MultimediaStore.MultimediaItem;
import com.example.jt808sim.fleet.VehicleState;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 0x0802 Response of store multimedia data retrieves (Tables 86-87, JT808-2013).
 *
 * Body:
 *   response serial  WORD
 *   total item count WORD
 *   retrieve items   [multimedia ID DWORD + type BYTE + channel BYTE + event BYTE + location BYTE[28]]×n
 */
public class StoreMediaRetrieveRespMessage extends AbstractJt808Message {
    private final int responseSerial;
    private final List<MultimediaItem> items;
    private final VehicleState vehicleState;

    public StoreMediaRetrieveRespMessage(int sequence, String terminalId,
                                          int responseSerial, List<MultimediaItem> items,
                                          VehicleState vehicleState) {
        super(sequence, terminalId, true);
        this.responseSerial = responseSerial;
        this.items          = items;
        this.vehicleState   = vehicleState;
    }

    @Override public int messageId() { return MessageIds.STORE_MEDIA_RETRIEVE_RESP; }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSerial);
        out.writeShort(items.size());
        for (MultimediaItem item : items) {
            out.writeInt((int) item.multimediaId());
            out.writeByte(item.mediaType());
            out.writeByte(item.channelId());
            out.writeByte(item.eventCode());
            encodeBasicLocationInfo(out, item);
        }
    }

    /** Writes the 28-byte fixed basic location info for a stored multimedia item. */
    private void encodeBasicLocationInfo(ByteBuf out, MultimediaItem item) {
        var coord = item.capturePosition();
        double speed = item.captureSpeedKph();
        out.writeInt((int) vehicleState.alarmWord());
        out.writeInt((int) vehicleState.statusWord(coord, speed));
        out.writeInt((int) Math.round(Math.abs(coord.latitude())  * 1_000_000));
        out.writeInt((int) Math.round(Math.abs(coord.longitude()) * 1_000_000));
        out.writeShort(vehicleState.altitudeMeters());
        out.writeShort((int) Math.round(speed * 10));
        out.writeShort(0); // heading not stored per item
        Jt808CodecSupport.writeBcdTimestamp(out, item.captureTime());
    }
}
