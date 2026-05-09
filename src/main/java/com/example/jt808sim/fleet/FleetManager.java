package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.netty.JT808ClientHandler;
import com.example.jt808sim.netty.Jt808EscapeCodec;
import com.example.jt808sim.netty.Jt808MessageDecoder;
import com.example.jt808sim.netty.Jt808MessageEncoder;
import com.example.jt808sim.netty.TransportSupport;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FleetManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(FleetManager.class);
    private static final int MAX_FRAME_LENGTH = 8192;

    private final FleetConfig config;
    private final MetricsRegistry metrics;
    private final EventLoopGroup eventLoopGroup;
    private final List<TerminalSession> sessions = new ArrayList<>();

    public FleetManager(FleetConfig config, MetricsRegistry metrics) {
        this.config = config;
        this.metrics = metrics;
        this.eventLoopGroup = createEventLoopGroup();
        this.metrics.configuredTerminals().set(config.getFleet().getConnectionCount());
    }

    public void start() {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(TransportSupport.socketChannelClass())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.TCP_NODELAY, true);

        List<VehicleIdentity> identities = expandIdentities();
        for (int i = 0; i < identities.size(); i++) {
            TerminalSession[] holder = new TerminalSession[1];
            Bootstrap sessionBootstrap = bootstrap.clone().handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast("frameDecoder", new DelimiterBasedFrameDecoder(MAX_FRAME_LENGTH, Unpooled.wrappedBuffer(new byte[]{0x7E})));
                    ch.pipeline().addLast("escapeCodec", new Jt808EscapeCodec());
                    ch.pipeline().addLast("messageDecoder", new Jt808MessageDecoder(metrics));
                    ch.pipeline().addLast("messageEncoder", new Jt808MessageEncoder());
                    ch.pipeline().addLast("clientHandler", new JT808ClientHandler(holder[0]));
                }
            });
            TerminalSession configuredSession = new TerminalSession(identities.get(i), config, sessionBootstrap, eventLoopGroup, metrics);
            holder[0] = configuredSession;
            sessions.add(configuredSession);
            configuredSession.connect(i * config.getFleet().getConnectStaggerMs());
        }
        log.info("started {} terminal sessions", sessions.size());
    }

    private List<VehicleIdentity> expandIdentities() {
        List<VehicleIdentity> result = new ArrayList<>(config.getFleet().getConnectionCount());
        for (int i = 0; i < config.getFleet().getConnectionCount(); i++) {
            VehicleIdentity template = config.getVehicles().get(i % config.getVehicles().size());
            VehicleIdentity identity = template.copyForIndex(i);
            if (i < config.getJt1078().getMediaCapableTerminalCount()) {
                identity.setMediaCapable(true);
                if (identity.getMediaChannels().isEmpty()) {
                    identity.setMediaChannels(List.of(1));
                }
            }
            result.add(identity);
        }
        return result;
    }

    private static EventLoopGroup createEventLoopGroup() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        return TransportSupport.newEventLoopGroup(threads);
    }

    @Override
    public void close() {
        sessions.forEach(TerminalSession::close);
        eventLoopGroup.shutdownGracefully();
    }
}
