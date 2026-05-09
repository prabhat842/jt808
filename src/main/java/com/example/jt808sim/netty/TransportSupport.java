package com.example.jt808sim.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public final class TransportSupport {
    public static final String TRANSPORT_PROPERTY = "jt808.transport";

    private TransportSupport() {
    }

    public static boolean useEpoll() {
        String requested = System.getProperty(TRANSPORT_PROPERTY, "auto").trim().toLowerCase();
        if ("nio".equals(requested)) {
            return false;
        }
        if ("epoll".equals(requested)) {
            return true;
        }
        return Epoll.isAvailable();
    }

    public static EventLoopGroup newEventLoopGroup(int threads) {
        return useEpoll() ? new EpollEventLoopGroup(threads) : new NioEventLoopGroup(threads);
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends SocketChannel> socketChannelClass() {
        return useEpoll() ? EpollSocketChannel.class : NioSocketChannel.class;
    }
}
