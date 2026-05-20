package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.fleet.LocationBuffer.LocationSnapshot;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.List;

/**
 * 0x0705 Accident suspect vehicle location data (Table 79, JT808-2013).
 *
 * Sent immediately when an emergency/collision alarm is triggered.
 * Contains the last N location reports to give investigators the vehicle's
 * position history leading up to the incident.
 *
 * Body layout:
 *   accidentTime BCD[6]
 *   n            WORD        number of location records
 *   locations    n × 28-byte basic location block
 */
public class AccidentSuspectReportMessage extends AbstractJt808Message {

    private final Instant            accidentTime;
    private final List<LocationSnapshot> snapshots;

    public AccidentSuspectReportMessage(int sequence, String terminalId,
                                         Instant accidentTime,
                                         List<LocationSnapshot> snapshots) {
        super(sequence, terminalId, true);
        this.accidentTime = accidentTime;
        this.snapshots    = snapshots;
    }

    @Override public int messageId() { return MessageIds.ACCIDENT_SUSPECT_REPORT; }

    @Override
    public void encodeBody(ByteBuf out) {
        Jt808CodecSupport.writeBcdTimestamp(out, accidentTime);
        out.writeShort(snapshots.size());
        for (LocationSnapshot s : snapshots) {
            BulkLocationUploadMessage.encodeBasicLocationBlock(out, s);
        }
    }
}
