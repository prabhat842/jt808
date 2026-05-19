package com.example.jt808sim.jt1078;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.ByteArrayOutputStream;

public class Jt1078PacketReassembler extends ChannelInboundHandlerAdapter {
    private ReassemblyState state;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Jt1078InboundPacket packet)) {
            super.channelRead(ctx, msg);
            return;
        }

        Jt1078InboundPacket assembled = switch (packet.subpackage()) {
            case 0 -> packet;
            case 1 -> begin(packet);
            case 2 -> end(packet);
            case 3 -> append(packet);
            default -> packet;
        };
        if (assembled != null) {
            ctx.fireChannelRead(assembled);
        }
    }

    private Jt1078InboundPacket begin(Jt1078InboundPacket packet) {
        state = new ReassemblyState(packet);
        state.payload().write(packet.payload(), 0, packet.payload().length);
        return null;
    }

    private Jt1078InboundPacket append(Jt1078InboundPacket packet) {
        if (!matches(packet)) {
            state = null;
            return null;
        }
        state.payload().write(packet.payload(), 0, packet.payload().length);
        return null;
    }

    private Jt1078InboundPacket end(Jt1078InboundPacket packet) {
        if (!matches(packet)) {
            state = null;
            return null;
        }
        state.payload().write(packet.payload(), 0, packet.payload().length);
        byte[] payload = state.payload().toByteArray();
        Jt1078InboundPacket assembled = new Jt1078InboundPacket(
                state.sequence(),
                state.terminalId(),
                state.channel(),
                state.frameType(),
                0,
                state.timestampMillis(),
                payload);
        state = null;
        return assembled;
    }

    private boolean matches(Jt1078InboundPacket packet) {
        return state != null
                && state.channel() == packet.channel()
                && state.frameType() == packet.frameType()
                && state.terminalId().equals(packet.terminalId());
    }

    private record ReassemblyState(
            long sequence,
            String terminalId,
            int channel,
            Jt1078FrameType frameType,
            long timestampMillis,
            ByteArrayOutputStream payload) {
        private ReassemblyState(Jt1078InboundPacket packet) {
            this(packet.sequence(), packet.terminalId(), packet.channel(), packet.frameType(), packet.timestampMillis(), new ByteArrayOutputStream());
        }
    }
}
