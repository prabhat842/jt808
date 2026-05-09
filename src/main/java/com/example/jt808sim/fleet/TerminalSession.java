package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.jt1078.Jt1078CommandHandler;
import com.example.jt808sim.jt1078.Jt1078MediaConfig;
import com.example.jt808sim.jt1078.Jt1078MediaSession;
import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.physics.TrajectoryEngine;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.OutboundJt808Message;
import com.example.jt808sim.protocol.RegistrationResponse;
import com.example.jt808sim.protocol.SequenceGenerator;
import com.example.jt808sim.protocol.ServerAck;
import com.example.jt808sim.protocol.messages.AuthenticationMessage;
import com.example.jt808sim.protocol.messages.HeartbeatMessage;
import com.example.jt808sim.protocol.messages.LocationReportMessage;
import com.example.jt808sim.protocol.messages.RegistrationMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TerminalSession {
    private static final Logger log = LoggerFactory.getLogger(TerminalSession.class);

    private final VehicleIdentity identity;
    private final FleetConfig config;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final MetricsRegistry metrics;
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();
    private final TrajectoryEngine trajectoryEngine;
    private final Map<Integer, PendingAck> responseMap = new ConcurrentHashMap<>();
    private final AtomicReference<TerminalState> state = new AtomicReference<>(TerminalState.DISCONNECTED);
    private final Jt1078MediaConfig mediaConfig;
    private final Jt1078CommandHandler mediaCommandHandler = new Jt1078CommandHandler();
    private Channel channel;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> locationTask;
    private int reconnectAttempt;
    private boolean authenticatedCounted;
    private List<Jt1078MediaSession> mediaSessions = List.of();

    public TerminalSession(VehicleIdentity identity, FleetConfig config, Bootstrap bootstrap, EventLoopGroup eventLoopGroup, MetricsRegistry metrics) {
        this.identity = identity;
        this.config = config;
        this.bootstrap = bootstrap;
        this.eventLoopGroup = eventLoopGroup;
        this.metrics = metrics;
        this.trajectoryEngine = new TrajectoryEngine(identity, config.getFleet().getRouteMode(), Instant.now());
        this.mediaConfig = Jt1078MediaConfig.from(config.getJt1078());
    }

    public VehicleIdentity identity() {
        return identity;
    }

    public TerminalState state() {
        return state.get();
    }

    public void connect(long delayMillis) {
        state.set(TerminalState.CONNECTING);
        eventLoopGroup.next().schedule(() -> {
            ChannelFuture future = bootstrap.connect(config.getServer().getHost(), config.getServer().getPort());
            future.addListener((ChannelFutureListener) connectFuture -> {
                if (connectFuture.isSuccess()) {
                    reconnectAttempt = 0;
                } else {
                    metrics.connectionFailures().increment();
                    scheduleReconnect();
                }
            });
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void onChannelActive(Channel channel) {
        this.channel = channel;
        state.set(TerminalState.CONNECTED);
        metrics.activeConnections().incrementAndGet();
        sendRegistration();
    }

    public void onChannelInactive() {
        metrics.activeConnections().decrementAndGet();
        if (authenticatedCounted) {
            metrics.authenticatedSessions().decrementAndGet();
            authenticatedCounted = false;
        }
        cancelStreamingTasks();
        stopMedia();
        responseMap.values().forEach(pending -> pending.future().cancel(false));
        responseMap.clear();
        if (state.get() != TerminalState.CLOSED) {
            scheduleReconnect();
        }
    }

    public void onMessage(Jt808Message message) {
        if (message.body() instanceof RegistrationResponse registrationResponse) {
            handleRegistrationResponse(registrationResponse);
        } else if (message.body() instanceof ServerAck ack) {
            handleServerAck(ack);
        } else if (mediaCommandHandler.isMediaCommand(message) && identity.isMediaCapable()) {
            startMediaIfCapable();
        }
    }

    public void onException(Throwable cause) {
        log.debug("terminal {} channel error", identity.getTerminalId(), cause);
        if (channel != null) {
            channel.close();
        }
    }

    public void close() {
        state.set(TerminalState.CLOSED);
        cancelStreamingTasks();
        stopMedia();
        if (channel != null) {
            channel.close();
        }
    }

    private void sendRegistration() {
        state.set(TerminalState.REGISTERING);
        write(new RegistrationMessage(sequenceGenerator.next(), identity));
    }

    private void handleRegistrationResponse(RegistrationResponse response) {
        if (!response.success()) {
            metrics.registrationFailures().increment();
            scheduleReconnect();
            return;
        }
        state.set(TerminalState.AUTHENTICATING);
        write(new AuthenticationMessage(sequenceGenerator.next(), identity.getTerminalId(), response.authCode()));
    }

    private void handleServerAck(ServerAck ack) {
        PendingAck pending = responseMap.remove(ack.responseSequence());
        if (pending != null && pending.messageId() == ack.responseMessageId()) {
            metrics.recordAckLatency(Duration.between(pending.sentAt(), Instant.now()).toMillis());
            pending.future().complete(ack);
        }
        if (state.get() == TerminalState.AUTHENTICATING && ack.responseMessageId() == MessageIds.TERMINAL_AUTH && ack.success()) {
            enterStreaming();
        }
    }

    private void enterStreaming() {
        state.set(TerminalState.STREAMING);
        if (!authenticatedCounted) {
            metrics.authenticatedSessions().incrementAndGet();
            authenticatedCounted = true;
        }
        scheduleStreamingTasks();
        startMediaIfCapable();
    }

    private void scheduleStreamingTasks() {
        cancelStreamingTasks();
        sendLocationSafely();
        heartbeatTask = channel.eventLoop().scheduleAtFixedRate(this::sendHeartbeatSafely,
                config.getFleet().getHeartbeatIntervalSeconds(),
                config.getFleet().getHeartbeatIntervalSeconds(),
                TimeUnit.SECONDS);
        locationTask = channel.eventLoop().scheduleAtFixedRate(this::sendLocationSafely,
                config.getFleet().getLocationIntervalSeconds(),
                config.getFleet().getLocationIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private void sendHeartbeatSafely() {
        try {
            sendHeartbeat();
        } catch (RuntimeException e) {
            log.warn("terminal {} heartbeat task failed", identity.getTerminalId(), e);
        }
    }

    private void sendLocationSafely() {
        try {
            sendLocation();
        } catch (RuntimeException e) {
            log.warn("terminal {} location task failed", identity.getTerminalId(), e);
        }
    }

    private void sendHeartbeat() {
        write(new HeartbeatMessage(sequenceGenerator.next(), identity.getTerminalId()), metrics.heartbeats()::increment);
    }

    private void sendLocation() {
        TrajectoryEngine.Snapshot snapshot = trajectoryEngine.snapshot(Instant.now());
        write(new LocationReportMessage(sequenceGenerator.next(), identity.getTerminalId(), snapshot.coordinate(), snapshot.speedKph(), snapshot.heading(), Instant.now()), metrics.locationReports()::increment);
    }

    private void write(OutboundJt808Message message) {
        write(message, null);
    }

    private void write(OutboundJt808Message message, Runnable onSuccess) {
        if (channel == null || !channel.isActive()) {
            metrics.skippedWrites().increment();
            return;
        }
        if (message.expectsServerAck()) {
            CompletableFuture<ServerAck> future = new CompletableFuture<>();
            PendingAck pending = new PendingAck(message.messageId(), Instant.now(), future);
            responseMap.put(message.sequence(), pending);
            channel.eventLoop().schedule(() -> {
                PendingAck removed = responseMap.remove(message.sequence());
                if (removed != null) {
                    removed.future().completeExceptionally(new IllegalStateException("ack timeout"));
                }
            }, config.getFleet().getAckTimeoutSeconds(), TimeUnit.SECONDS);
        }
        channel.writeAndFlush(message).addListener(future -> {
            if (future.isSuccess()) {
                metrics.outboundMessages().increment();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } else {
                metrics.outboundFailures().increment();
                log.warn("terminal {} failed to write message 0x{}", identity.getTerminalId(), Integer.toHexString(message.messageId()), future.cause());
            }
        });
    }

    private void scheduleReconnect() {
        if (state.get() == TerminalState.CLOSED) {
            return;
        }
        state.set(TerminalState.RECONNECTING);
        metrics.reconnectAttempts().increment();
        long baseSeconds = Math.min(60, 1L << Math.min(6, reconnectAttempt++));
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 1000);
        connect(baseSeconds * 1000 + jitterMillis);
    }

    private void startMediaIfCapable() {
        if (!identity.isMediaCapable() || identity.getMediaChannels().isEmpty()) {
            return;
        }
        if (!mediaSessions.isEmpty()) {
            return;
        }
        mediaSessions = identity.getMediaChannels().stream()
                .map(channelId -> new Jt1078MediaSession(eventLoopGroup, mediaConfig, metrics, identity.getTerminalId(), channelId))
                .toList();
        mediaSessions.forEach(Jt1078MediaSession::start);
    }

    private void stopMedia() {
        mediaSessions.forEach(Jt1078MediaSession::stop);
        mediaSessions = List.of();
    }

    private void cancelStreamingTasks() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (locationTask != null) {
            locationTask.cancel(false);
            locationTask = null;
        }
    }
}
