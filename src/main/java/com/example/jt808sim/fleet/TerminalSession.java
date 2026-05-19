package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.jt1078.Jt1078Command;
import com.example.jt808sim.jt1078.Jt1078MediaConfig;
import com.example.jt808sim.jt1078.Jt1078MediaSession;
import com.example.jt808sim.jt1078.Jt1078SessionRequest;
import com.example.jt808sim.jt1078.PlaybackSelection;
import com.example.jt808sim.jt1078.TerminalMediaCatalog;
import com.example.jt808sim.jt1078.messages.AudioVideoAttributesMessage;
import com.example.jt808sim.jt1078.messages.FileUploadCompletionMessage;
import com.example.jt808sim.jt1078.messages.RealTimeStatusMessage;
import com.example.jt808sim.jt1078.messages.ResourceListUploadMessage;
import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.physics.Coordinate;
import com.example.jt808sim.physics.Haversine;
import com.example.jt808sim.physics.TrajectoryEngine;
import com.example.jt808sim.protocol.Jt808Message;
import com.example.jt808sim.protocol.MessageIds;
import com.example.jt808sim.protocol.OutboundJt808Message;
import com.example.jt808sim.protocol.ParameterSetting;
import com.example.jt808sim.protocol.RegistrationResponse;
import com.example.jt808sim.protocol.SequenceGenerator;
import com.example.jt808sim.protocol.ServerAck;
import com.example.jt808sim.fleet.geofence.GeofenceStore;
import com.example.jt808sim.protocol.inbound.DeleteArea;
import com.example.jt808sim.protocol.inbound.DeleteRoute;
import com.example.jt808sim.protocol.inbound.LocationQuery;
import com.example.jt808sim.protocol.inbound.ManualAlarmConfirm;
import com.example.jt808sim.protocol.inbound.SetCircleArea;
import com.example.jt808sim.protocol.inbound.SetPolygonArea;
import com.example.jt808sim.protocol.inbound.SetRectangleArea;
import com.example.jt808sim.protocol.inbound.SetRoute;
import com.example.jt808sim.protocol.inbound.TempLocationTracking;
import com.example.jt808sim.protocol.inbound.TerminalAttributeQuery;
import com.example.jt808sim.protocol.inbound.TerminalControl;
import com.example.jt808sim.protocol.inbound.TerminalParamQueryAll;
import com.example.jt808sim.protocol.inbound.TerminalParamQuerySpec;
import com.example.jt808sim.protocol.inbound.TerminalUpdate;
import com.example.jt808sim.protocol.messages.AuthenticationMessage;
import com.example.jt808sim.protocol.messages.HeartbeatMessage;
import com.example.jt808sim.protocol.messages.LocationQueryResponseMessage;
import com.example.jt808sim.protocol.messages.LocationReportMessage;
import com.example.jt808sim.protocol.messages.LogoutMessage;
import com.example.jt808sim.protocol.messages.RegistrationMessage;
import com.example.jt808sim.protocol.messages.TerminalAttributeResponseMessage;
import com.example.jt808sim.protocol.messages.TerminalGeneralResponseMessage;
import com.example.jt808sim.protocol.messages.TerminalParamQueryResponseMessage;
import com.example.jt808sim.protocol.messages.UpgradeResultMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
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
    private final TerminalParams params;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final MetricsRegistry metrics;
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();
    private final TrajectoryEngine trajectoryEngine;
    private final VehicleState vehicleState = new VehicleState();
    private final AlarmState alarmState = new AlarmState();
    private final AlarmEngine alarmEngine;
    private final GeofenceStore geofenceStore;
    private final Map<Integer, PendingAck> responseMap = new ConcurrentHashMap<>();
    private final AtomicReference<TerminalState> state = new AtomicReference<>(TerminalState.DISCONNECTED);
    private final Jt1078MediaConfig mediaConfig;
    private final TerminalMediaCatalog mediaCatalog;

    private Channel channel;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> locationTask;
    private ScheduledFuture<?> tempTrackingTask;
    private ScheduledFuture<?> tempTrackingRevertTask;
    private Coordinate lastKnownPosition;
    private int reconnectAttempt;
    private boolean authenticatedCounted;
    private final Map<Integer, Jt1078MediaSession> mediaSessions = new ConcurrentHashMap<>();

    public TerminalSession(VehicleIdentity identity, FleetConfig config, Bootstrap bootstrap,
                           EventLoopGroup eventLoopGroup, MetricsRegistry metrics) {
        this.identity = identity;
        this.config = config;
        this.params = TerminalParams.from(config);
        this.bootstrap = bootstrap;
        this.eventLoopGroup = eventLoopGroup;
        this.metrics = metrics;
        this.trajectoryEngine = new TrajectoryEngine(identity, config.getFleet().getRouteMode(), Instant.now());
        this.alarmEngine    = new AlarmEngine(identity.getTerminalId());
        this.geofenceStore  = new GeofenceStore(identity.getTerminalId());
        this.mediaConfig = Jt1078MediaConfig.from(config.getJt1078());
        this.mediaCatalog = TerminalMediaCatalog.seed(identity);
    }

    public VehicleIdentity identity() { return identity; }
    public TerminalState state()      { return state.get(); }

    public void connect(long delayMillis) {
        state.set(TerminalState.CONNECTING);
        eventLoopGroup.next().schedule(() -> {
            ChannelFuture future = bootstrap.connect(params.serverHost(), params.serverPort());
            future.addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
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
        responseMap.values().forEach(p -> p.future().cancel(false));
        responseMap.clear();
        if (state.get() != TerminalState.CLOSED) {
            scheduleReconnect();
        }
    }

    public void onMessage(Jt808Message message) {
        if (message.body() instanceof RegistrationResponse rr) {
            handleRegistrationResponse(rr);
        } else if (message.body() instanceof ServerAck ack) {
            handleServerAck(ack);
        } else if (message.body() instanceof ParameterSetting setting) {
            handleParameterSetting(message, setting);
        } else if (message.body() instanceof LocationQuery) {
            handleLocationQuery(message);
        } else if (message.body() instanceof TempLocationTracking tracking) {
            handleTempLocationTracking(message, tracking);
        } else if (message.body() instanceof ManualAlarmConfirm confirm) {
            handleManualAlarmConfirm(message, confirm);
        } else if (message.body() instanceof TerminalParamQueryAll) {
            handleTerminalParamQueryAll(message);
        } else if (message.body() instanceof TerminalParamQuerySpec spec) {
            handleTerminalParamQuerySpec(message, spec);
        } else if (message.body() instanceof TerminalControl control) {
            handleTerminalControl(message, control);
        } else if (message.body() instanceof TerminalAttributeQuery) {
            handleTerminalAttributeQuery(message);
        } else if (message.body() instanceof TerminalUpdate update) {
            handleTerminalUpdate(message, update);
        } else if (message.body() instanceof SetCircleArea cmd) {
            handleSetCircleArea(message, cmd);
        } else if (message.body() instanceof SetRectangleArea cmd) {
            handleSetRectangleArea(message, cmd);
        } else if (message.body() instanceof SetPolygonArea cmd) {
            handleSetPolygonArea(message, cmd);
        } else if (message.body() instanceof SetRoute cmd) {
            handleSetRoute(message, cmd);
        } else if (message.body() instanceof DeleteArea cmd) {
            handleDeleteArea(message, cmd, message.header().messageId());
        } else if (message.body() instanceof DeleteRoute cmd) {
            handleDeleteRoute(message, cmd);
        } else if (message.body() instanceof Jt1078Command command) {
            handleJt1078Command(message, command);
        }
    }

    public void onException(Throwable cause) {
        log.debug("terminal {} channel error", identity.getTerminalId(), cause);
        if (channel != null) channel.close();
    }

    public void close() {
        state.set(TerminalState.CLOSED);
        cancelStreamingTasks();
        stopMedia();
        if (channel != null && channel.isActive()) {
            write(new LogoutMessage(sequenceGenerator.next(), identity.getTerminalId()));
            channel.close();
        }
    }

    // ── Session lifecycle ────────────────────────────────────────────────────

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
        if (state.get() == TerminalState.AUTHENTICATING
                && ack.responseMessageId() == MessageIds.TERMINAL_AUTH && ack.success()) {
            enterStreaming();
        }
        // Platform ACKing a location report clears the ON_ACK alarm bits (e.g. area entry/exit)
        if (ack.responseMessageId() == MessageIds.LOCATION_REPORT && ack.success()) {
            alarmState.clearOnAckBits();
        }
    }

    private void handleParameterSetting(Jt808Message message, ParameterSetting setting) {
        ack(message, MessageIds.TERMINAL_PARAM_SETTING, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        boolean reschedule = params.apply(identity.getTerminalId(), setting);
        if (reschedule && state.get() == TerminalState.STREAMING) {
            scheduleStreamingTasks();
        }
    }

    private void enterStreaming() {
        state.set(TerminalState.STREAMING);
        if (!authenticatedCounted) {
            metrics.authenticatedSessions().incrementAndGet();
            authenticatedCounted = true;
        }
        scheduleStreamingTasks();
    }

    // ── Phase 1: new platform → terminal handlers ────────────────────────────

    private void handleLocationQuery(Jt808Message message) {
        TrajectoryEngine.Snapshot snapshot = trajectoryEngine.snapshot(Instant.now());
        write(new LocationQueryResponseMessage(
                sequenceGenerator.next(), identity.getTerminalId(),
                message.header().sequence(),
                snapshot.coordinate(), snapshot.speedKph(), snapshot.heading(),
                Instant.now(), vehicleState));
    }

    private void handleTempLocationTracking(Jt808Message message, TempLocationTracking tracking) {
        ack(message, MessageIds.TEMP_LOCATION_TRACKING, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        if (tracking.intervalSeconds() == 0) {
            cancelTempTracking();
            log.debug("terminal {} temp tracking stopped", identity.getTerminalId());
        } else {
            startTempTracking(tracking.intervalSeconds(), tracking.validitySeconds());
            log.debug("terminal {} temp tracking {}s for {}s",
                    identity.getTerminalId(), tracking.intervalSeconds(), tracking.validitySeconds());
        }
    }

    private void handleManualAlarmConfirm(Jt808Message message, ManualAlarmConfirm confirm) {
        ack(message, MessageIds.MANUAL_ALARM_CONFIRM, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        alarmState.confirmAlarms(confirm.alarmTypeMask());
        log.debug("terminal {} alarm confirm serial={} mask=0x{} remaining=0x{}",
                identity.getTerminalId(), confirm.serialNumber(),
                Long.toHexString(confirm.alarmTypeMask()),
                Long.toHexString(alarmState.toAlarmWord()));
    }

    private void handleTerminalParamQueryAll(Jt808Message message) {
        write(new TerminalParamQueryResponseMessage(
                sequenceGenerator.next(), identity.getTerminalId(),
                message.header().sequence(), params.allParameters()));
    }

    private void handleTerminalParamQuerySpec(Jt808Message message, TerminalParamQuerySpec spec) {
        write(new TerminalParamQueryResponseMessage(
                sequenceGenerator.next(), identity.getTerminalId(),
                message.header().sequence(), params.parametersFor(spec.paramIds())));
    }

    private void handleTerminalControl(Jt808Message message, TerminalControl control) {
        ack(message, MessageIds.TERMINAL_CONTROL, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        log.info("terminal {} control command {}", identity.getTerminalId(), control.command());
        switch (control.command()) {
            case 3, 7 -> {  // power off / close all wireless
                close();
            }
            case 4 -> {     // reset — disconnect and let reconnect logic re-register
                if (channel != null) channel.close();
            }
            case 5 -> {     // factory reset — restore defaults then reconnect
                params.reset(config);
                if (channel != null) channel.close();
            }
            default -> {    // commands 1 (OTA upgrade) and 2 (connect to server) handled separately
            }
        }
    }

    private void handleTerminalAttributeQuery(Jt808Message message) {
        write(new TerminalAttributeResponseMessage(
                sequenceGenerator.next(), identity.getTerminalId(), identity));
    }

    private void handleTerminalUpdate(Jt808Message message, TerminalUpdate update) {
        ack(message, MessageIds.TERMINAL_UPDATE, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        log.info("terminal {} OTA update type={} version={}", identity.getTerminalId(),
                update.upgradeType(), update.version());
        // Simulate upgrade: 2 s delay, then report success
        channel.eventLoop().schedule(() -> {
            if (channel != null && channel.isActive()) {
                write(new UpgradeResultMessage(
                        sequenceGenerator.next(), identity.getTerminalId(),
                        update.upgradeType(), 0));
            }
        }, 2, TimeUnit.SECONDS);
    }

    // ── Phase 3: geofence / vehicle management ────────────────────────────────

    private void handleSetCircleArea(Jt808Message message, SetCircleArea cmd) {
        ack(message, MessageIds.SET_CIRCLE_AREA, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        geofenceStore.setCircles(cmd.settingAttribute(), cmd.areas());
    }

    private void handleSetRectangleArea(Jt808Message message, SetRectangleArea cmd) {
        ack(message, MessageIds.SET_RECTANGLE_AREA, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        geofenceStore.setRectangles(cmd.settingAttribute(), cmd.areas());
    }

    private void handleSetPolygonArea(Jt808Message message, SetPolygonArea cmd) {
        ack(message, MessageIds.SET_POLYGON_AREA, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        geofenceStore.setPolygon(cmd.settingAttribute(), cmd.area());
    }

    private void handleSetRoute(Jt808Message message, SetRoute cmd) {
        ack(message, MessageIds.SET_ROUTE, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        geofenceStore.setRoute(cmd.route());
    }

    private void handleDeleteArea(Jt808Message message, DeleteArea cmd, int messageId) {
        ack(message, messageId, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        if (messageId == MessageIds.DELETE_CIRCLE_AREA) {
            geofenceStore.deleteCircles(cmd.areaIds());
        } else if (messageId == MessageIds.DELETE_RECTANGLE_AREA) {
            geofenceStore.deleteRectangles(cmd.areaIds());
        } else if (messageId == MessageIds.DELETE_POLYGON_AREA) {
            geofenceStore.deletePolygons(cmd.areaIds());
        }
    }

    private void handleDeleteRoute(Jt808Message message, DeleteRoute cmd) {
        ack(message, MessageIds.DELETE_ROUTE, TerminalGeneralResponseMessage.RESULT_SUCCESS);
        geofenceStore.deleteRoutes(cmd.routeIds());
    }

    // ── JT1078 media signaling ────────────────────────────────────────────────

    private void handleJt1078Command(Jt808Message message, Jt1078Command command) {
        if (!identity.isMediaCapable()) {
            write(new TerminalGeneralResponseMessage(sequenceGenerator.next(), identity.getTerminalId(),
                    message.header().sequence(), message.header().messageId(),
                    TerminalGeneralResponseMessage.RESULT_UNSUPPORTED));
            return;
        }
        write(new TerminalGeneralResponseMessage(sequenceGenerator.next(), identity.getTerminalId(),
                message.header().sequence(), message.header().messageId(),
                TerminalGeneralResponseMessage.RESULT_SUCCESS));
        switch (command) {
            case Jt1078Command.QueryAudioVideoAttributes ignored ->
                    write(new AudioVideoAttributesMessage(
                            sequenceGenerator.next(), identity.getTerminalId(),
                            mediaConfig, identity.getMediaChannels()));
            case Jt1078Command.RealTimeRequest request -> {
                startMedia(Jt1078SessionRequest.fromRealTimeRequest(request),
                        mediaConfig.withEndpoint(request.host(), request.preferredPort()));
                write(new RealTimeStatusMessage(sequenceGenerator.next(), identity.getTerminalId(),
                        request.channel(), 0));
            }
            case Jt1078Command.RealTimeControl control -> handleRealTimeControl(control);
            case Jt1078Command.QueryResourceList query ->
                    write(new ResourceListUploadMessage(
                            sequenceGenerator.next(), identity.getTerminalId(),
                            message.header().sequence(), mediaCatalog.query(query)));
            case Jt1078Command.PlaybackRequest request -> handlePlaybackRequest(request);
            case Jt1078Command.PlaybackControl control -> handlePlaybackControl(control);
            case Jt1078Command.FileUploadCommand request ->
                    write(new FileUploadCompletionMessage(
                            sequenceGenerator.next(), identity.getTerminalId(),
                            message.header().sequence(),
                            mediaCatalog.uploadTargets(request).isEmpty() ? 1 : 0));
            case Jt1078Command.FileUploadControl ignored -> { }
            case Jt1078Command.PtzControl ignored -> { }
            case Jt1078Command.SimpleChannelControl ignored -> { }
        }
    }

    private void handleRealTimeControl(Jt1078Command.RealTimeControl control) {
        if (control.command() == 0 || control.command() == 2 || control.command() == 4) {
            stopMedia(control.channel());
        } else if (control.command() == 3) {
            Jt1078MediaSession existing = mediaSessions.get(control.channel());
            if (existing != null) existing.start();
        }
    }

    private void handlePlaybackControl(Jt1078Command.PlaybackControl control) {
        Jt1078MediaSession session = mediaSessions.get(control.channel());
        if (session == null) return;
        switch (control.command()) {
            case 0 -> session.resumePlayback();
            case 1 -> session.pausePlayback();
            case 2 -> stopMedia(control.channel());
            case 3, 4 -> session.setPlaybackSpeed(control.speed());
            case 5 -> session.seekPlayback(control.playbackPosition());
            case 6 -> { session.setPlaybackSpeed(1); session.resumePlayback(); }
            default -> { }
        }
    }

    private void handlePlaybackRequest(Jt1078Command.PlaybackRequest request) {
        PlaybackSelection selection = mediaCatalog.playbackTarget(request);
        if (selection == null) return;
        long endTicks = Math.max(1, Duration.between(
                selection.effectiveStartTime(), selection.effectiveEndTime()).toSeconds() * 25L);
        startMedia(
                Jt1078SessionRequest.fromPlaybackRequest(request).withPlaybackWindow(
                        0, endTicks, selection.effectiveStartTime(), selection.effectiveEndTime()),
                mediaConfig.withEndpoint(request.host(), request.preferredPort()));
    }

    // ── Streaming tasks ───────────────────────────────────────────────────────

    private void scheduleStreamingTasks() {
        cancelStreamingTasks();
        sendLocationSafely();
        heartbeatTask = channel.eventLoop().scheduleAtFixedRate(
                this::sendHeartbeatSafely,
                params.heartbeatIntervalSeconds(), params.heartbeatIntervalSeconds(), TimeUnit.SECONDS);
        locationTask = channel.eventLoop().scheduleAtFixedRate(
                this::sendLocationSafely,
                params.locationIntervalSeconds(), params.locationIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void startTempTracking(int intervalSeconds, long validitySeconds) {
        cancelTempTracking();
        tempTrackingTask = channel.eventLoop().scheduleAtFixedRate(
                this::sendLocationSafely, 0, intervalSeconds, TimeUnit.SECONDS);
        if (validitySeconds > 0) {
            tempTrackingRevertTask = channel.eventLoop().schedule(
                    this::cancelTempTracking, validitySeconds, TimeUnit.SECONDS);
        }
    }

    private void cancelTempTracking() {
        if (tempTrackingTask != null)       { tempTrackingTask.cancel(false);       tempTrackingTask = null; }
        if (tempTrackingRevertTask != null) { tempTrackingRevertTask.cancel(false); tempTrackingRevertTask = null; }
    }

    private void cancelStreamingTasks() {
        if (heartbeatTask != null) { heartbeatTask.cancel(false); heartbeatTask = null; }
        if (locationTask != null)  { locationTask.cancel(false);  locationTask = null; }
        cancelTempTracking();
    }

    private void sendHeartbeatSafely() {
        try { sendHeartbeat(); } catch (RuntimeException e) {
            log.warn("terminal {} heartbeat task failed", identity.getTerminalId(), e);
        }
    }

    private void sendLocationSafely() {
        try { sendLocation(); } catch (RuntimeException e) {
            log.warn("terminal {} location task failed", identity.getTerminalId(), e);
        }
    }

    private void sendHeartbeat() {
        write(new HeartbeatMessage(sequenceGenerator.next(), identity.getTerminalId()),
                metrics.heartbeats()::increment);
    }

    private void sendLocation() {
        Instant now = Instant.now();
        TrajectoryEngine.Snapshot snapshot = trajectoryEngine.snapshot(now);

        // Accumulate odometer from last known position
        if (lastKnownPosition != null) {
            vehicleState.addDistanceMeters(Haversine.distanceMeters(lastKnownPosition, snapshot.coordinate()));
        }
        lastKnownPosition = snapshot.coordinate();

        // Evaluate geofences; this may set alarm bits 20/21/23 and record area alarm info
        geofenceStore.evaluate(snapshot.coordinate(), snapshot.speedKph(), alarmState, now);
        vehicleState.setAreaAlarmInfo(geofenceStore.latestAreaAlarmInfo());

        // Evaluate speed/fatigue/parking alarms using area-specific speed limit if active
        long effectiveMaxSpeed = geofenceStore.effectiveMaxSpeedKph() > 0
                ? geofenceStore.effectiveMaxSpeedKph() : params.maxSpeedKph();
        alarmEngine.evaluate(snapshot.speedKph(), effectiveMaxSpeed, alarmState, params, now);
        vehicleState.setAlarmWord(alarmState.toAlarmWord());

        write(new LocationReportMessage(
                sequenceGenerator.next(), identity.getTerminalId(),
                snapshot.coordinate(), snapshot.speedKph(), snapshot.heading(),
                now, vehicleState),
                metrics.locationReports()::increment);
    }

    // ── Media session management ──────────────────────────────────────────────

    private void startMedia(Jt1078SessionRequest request, Jt1078MediaConfig config) {
        int ch = request.channel();
        if (!identity.isMediaCapable() || ch <= 0) return;
        Jt1078MediaSession existing = mediaSessions.remove(ch);
        if (existing != null) existing.stop();
        Jt1078MediaSession session = new Jt1078MediaSession(
                eventLoopGroup, config, request, metrics, identity.getTerminalId(), ch);
        session.start();
        mediaSessions.put(ch, session);
    }

    private void stopMedia(int channelId) {
        Jt1078MediaSession session = mediaSessions.remove(channelId);
        if (session != null) session.stop();
    }

    private void stopMedia() {
        mediaSessions.values().forEach(Jt1078MediaSession::stop);
        mediaSessions.clear();
    }

    // ── Write helpers ─────────────────────────────────────────────────────────

    /** Sends a terminal general response (0x0001) for the given received message. */
    private void ack(Jt808Message message, int messageId, int result) {
        write(new TerminalGeneralResponseMessage(
                sequenceGenerator.next(), identity.getTerminalId(),
                message.header().sequence(), messageId, result));
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
                if (removed != null) removed.future().completeExceptionally(
                        new IllegalStateException("ack timeout"));
            }, params.ackTimeoutSeconds(), TimeUnit.SECONDS);
        }
        channel.writeAndFlush(message).addListener(f -> {
            if (f.isSuccess()) {
                metrics.outboundMessages().increment();
                if (onSuccess != null) onSuccess.run();
            } else {
                metrics.outboundFailures().increment();
                log.warn("terminal {} failed to write 0x{}", identity.getTerminalId(),
                        Integer.toHexString(message.messageId()), f.cause());
            }
        });
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private void scheduleReconnect() {
        if (state.get() == TerminalState.CLOSED) return;
        state.set(TerminalState.RECONNECTING);
        metrics.reconnectAttempts().increment();
        long baseSeconds = Math.min(60, 1L << Math.min(6, reconnectAttempt++));
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 1000);
        connect(baseSeconds * 1000 + jitterMillis);
    }
}
