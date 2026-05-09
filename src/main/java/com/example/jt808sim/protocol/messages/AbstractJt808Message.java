package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.OutboundJt808Message;

public abstract class AbstractJt808Message implements OutboundJt808Message {
    private final int sequence;
    private final String terminalId;
    private final boolean expectsServerAck;

    protected AbstractJt808Message(int sequence, String terminalId, boolean expectsServerAck) {
        this.sequence = sequence;
        this.terminalId = terminalId;
        this.expectsServerAck = expectsServerAck;
    }

    @Override
    public int sequence() {
        return sequence;
    }

    @Override
    public String terminalId() {
        return terminalId;
    }

    @Override
    public boolean expectsServerAck() {
        return expectsServerAck;
    }
}
