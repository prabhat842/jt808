package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.protocol.Jt808CodecSupport;
import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

public class RegistrationMessage extends AbstractJt808Message {
    private final VehicleIdentity identity;

    public RegistrationMessage(int sequence, VehicleIdentity identity) {
        super(sequence, identity.getTerminalId(), false);
        this.identity = identity;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_REGISTER;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(0);
        out.writeShort(0);
        Jt808CodecSupport.writeFixedAscii(out, identity.getManufacturerId(), 11);
        Jt808CodecSupport.writeFixedAscii(out, identity.getVin(), 30);
        Jt808CodecSupport.writeFixedAscii(out, identity.getTerminalId(), 30);
        out.writeByte(2);
        out.writeBytes(identity.getPlateNumber().getBytes(Jt808CodecSupport.GBK));
    }
}
