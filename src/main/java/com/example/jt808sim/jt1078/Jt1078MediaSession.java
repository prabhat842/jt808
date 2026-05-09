package com.example.jt808sim.jt1078;

import com.example.jt808sim.monitoring.MetricsRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Jt1078MediaSession {
    private final EventLoopGroup eventLoopGroup;
    private final Jt1078MediaConfig config;
    private final MetricsRegistry metrics;
    private final String terminalId;
    private final int channelId;
    private final SyntheticMediaSource source = new SyntheticMediaSource();
    private final AtomicLong sequence = new AtomicLong();
    private Channel channel;
    private ScheduledFuture<?> sendTask;

    public Jt1078MediaSession(EventLoopGroup eventLoopGroup, Jt1078MediaConfig config, MetricsRegistry metrics, String terminalId, int channelId) {
        this.eventLoopGroup = eventLoopGroup;
        this.config = config;
        this.metrics = metrics;
        this.terminalId = terminalId;
        this.channelId = channelId;
    }

    public void start() {
        if (channel != null && channel.isActive()) {
            return;
        }
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                    }
                });
        bootstrap.connect(config.host(), config.port()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                metrics.activeMediaSessions().incrementAndGet();
                schedulePackets();
                channel.closeFuture().addListener(ignored -> {
                    metrics.activeMediaSessions().decrementAndGet();
                    cancel();
                });
            } else {
                metrics.mediaConnectionFailures().increment();
            }
        });
    }

    private void schedulePackets() {
        long periodMillis = Math.max(1, 1000 / Math.max(1, config.packetsPerSecond()));
        sendTask = channel.eventLoop().scheduleAtFixedRate(this::sendPacket, 0, periodMillis, TimeUnit.MILLISECONDS);
    }

    private void sendPacket() {
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            return;
        }
        ByteBuf packet = channel.alloc().buffer(config.payloadBytesPerPacket() + 32);
        new Jt1078MediaPacket(terminalId, channelId, sequence.incrementAndGet(), source, config.payloadBytesPerPacket()).encode(packet);
        int bytes = packet.readableBytes();
        channel.writeAndFlush(packet).addListener(future -> {
            if (future.isSuccess()) {
                metrics.mediaPackets().increment();
                metrics.mediaBytes().add(bytes);
            }
        });
    }

    public void stop() {
        cancel();
        if (channel != null) {
            channel.close();
        }
    }

    private void cancel() {
        if (sendTask != null) {
            sendTask.cancel(false);
            sendTask = null;
        }
    }
}
