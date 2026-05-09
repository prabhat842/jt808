package com.example.jt808sim.netty;

import com.example.jt808sim.fleet.TerminalSession;
import com.example.jt808sim.protocol.Jt808Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class JT808ClientHandler extends SimpleChannelInboundHandler<Jt808Message> {
    private final TerminalSession session;

    public JT808ClientHandler(TerminalSession session) {
        this.session = session;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session.onChannelActive(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        session.onChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Jt808Message message) {
        session.onMessage(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        session.onException(cause);
    }
}
