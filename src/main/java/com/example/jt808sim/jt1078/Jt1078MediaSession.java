package com.example.jt808sim.jt1078;

import com.example.jt808sim.monitoring.MetricsRegistry;
import com.example.jt808sim.netty.TransportSupport;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Jt1078MediaSession {
    private enum PlaybackState {
        RUNNING,
        PAUSED,
        STOPPED
    }

    private final EventLoopGroup eventLoopGroup;
    private final Jt1078MediaConfig config;
    private final Jt1078SessionRequest request;
    private final MetricsRegistry metrics;
    private final String terminalId;
    private final int channelId;
    private final Jt1078FrameSource source;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong frameIndex = new AtomicLong();
    private final AtomicLong playbackPositionTicks = new AtomicLong();
    private final AtomicReference<PlaybackState> playbackState = new AtomicReference<>(PlaybackState.RUNNING);
    private volatile int playbackSpeedMultiplier = 1;
    private Channel channel;
    private ScheduledFuture<?> sendTask;

    public Jt1078MediaSession(EventLoopGroup eventLoopGroup, Jt1078MediaConfig config, Jt1078SessionRequest request, MetricsRegistry metrics, String terminalId, int channelId) {
        this.eventLoopGroup = eventLoopGroup;
        this.config = config;
        this.request = request;
        this.metrics = metrics;
        this.terminalId = terminalId;
        this.channelId = channelId;
        this.source = createSource(config, request);
        this.playbackPositionTicks.set(request.playbackStartTicks());
        this.playbackSpeedMultiplier = Math.max(1, request.playbackSpeed());
    }

    public void start() {
        if (channel != null && channel.isActive()) {
            playbackState.set(PlaybackState.RUNNING);
            return;
        }
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(TransportSupport.socketChannelClass())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (request.downlinkAudio()) {
                            ch.pipeline()
                                    .addLast(new Jt1078PacketDecoder())
                                    .addLast(new Jt1078PacketReassembler())
                                    .addLast(new Jt1078DownlinkAudioHandler(
                                            metrics,
                                            terminalId,
                                            channelId,
                                            config));
                        }
                    }
                });
        bootstrap.connect(config.host(), config.port()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                metrics.activeMediaSessions().incrementAndGet();
                if (request.hasUplinkMedia()) {
                    schedulePackets();
                }
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
        long periodMillis = request.uplinkAudio()
                ? 20
                : Math.max(1, 1000 / Math.max(1, config.packetsPerSecond()));
        sendTask = channel.eventLoop().scheduleAtFixedRate(this::sendPacket, 0, periodMillis, TimeUnit.MILLISECONDS);
    }

    private void sendPacket() {
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            return;
        }
        if (request.playbackMode() && playbackState.get() != PlaybackState.RUNNING) {
            return;
        }
        long nextIndex = request.playbackMode()
                ? playbackPositionTicks.addAndGet(Math.max(1, playbackSpeedMultiplier))
                : frameIndex.incrementAndGet();
        if (request.playbackMode() && nextIndex > request.playbackEndTicks()) {
            playbackState.set(PlaybackState.STOPPED);
            cancel();
            return;
        }
        for (Jt1078Frame frame : source.nextFrames(nextIndex)) {
            if (frame == null || frame.payload().length == 0) {
                continue;
            }
            long sequenceBase = sequence.get() + 1;
            var packets = Jt1078Packetizer.packetize(
                    terminalId,
                    channelId,
                    sequenceBase,
                    frame,
                    config.payloadBytesPerPacket());
            sequence.addAndGet(packets.size());
            for (Jt1078MediaPacket mediaPacket : packets) {
                ByteBuf packet = channel.alloc().buffer(frame.payload().length + 32);
                mediaPacket.encode(packet);
                int bytes = packet.readableBytes();
                channel.writeAndFlush(packet).addListener(future -> {
                    if (future.isSuccess()) {
                        metrics.mediaPackets().increment();
                        metrics.mediaBytes().add(bytes);
                    }
                });
            }
        }
    }

    public void stop() {
        playbackState.set(PlaybackState.STOPPED);
        cancel();
        source.close();
        if (channel != null) {
            channel.close();
        }
    }

    public void pausePlayback() {
        if (request.playbackMode()) {
            playbackState.set(PlaybackState.PAUSED);
        }
    }

    public void resumePlayback() {
        if (request.playbackMode()) {
            playbackState.set(PlaybackState.RUNNING);
        }
    }

    public void setPlaybackSpeed(int speedCode) {
        if (!request.playbackMode()) {
            return;
        }
        playbackSpeedMultiplier = switch (speedCode) {
            case 0 -> 1;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 8;
            case 5 -> 16;
            default -> 1;
        };
    }

    public void seekPlayback(byte[] playbackPosition) {
        if (!request.playbackMode() || playbackPosition == null || playbackPosition.length < 6) {
            return;
        }
        Instant startTime = request.playbackStartTime();
        Instant endTime = request.playbackEndTime();
        if (startTime != null && endTime != null) {
            Instant requestedTime = decodeTimestamp(playbackPosition);
            if (requestedTime != null) {
                Instant clamped = requestedTime.isBefore(startTime)
                        ? startTime
                        : requestedTime.isAfter(endTime) ? endTime : requestedTime;
                playbackPositionTicks.set(Math.max(0, Duration.between(startTime, clamped).toSeconds() * 25L));
                playbackState.set(PlaybackState.RUNNING);
                return;
            }
        }
        long hh = bcd(playbackPosition[3]);
        long mm = bcd(playbackPosition[4]);
        long ss = bcd(playbackPosition[5]);
        long seconds = hh * 3600 + mm * 60 + ss;
        playbackPositionTicks.set(seconds * 25L);
        playbackState.set(PlaybackState.RUNNING);
    }

    private void cancel() {
        if (sendTask != null) {
            sendTask.cancel(false);
            sendTask = null;
        }
    }

    static Jt1078FrameSource createSource(Jt1078MediaConfig config, Jt1078SessionRequest request) {
        if ("camera".equalsIgnoreCase(config.streamMode())) {
            boolean videoEnabled = config.capture().videoEnabled() && request.uplinkVideo();
            boolean audioEnabled = config.capture().audioEnabled() && request.uplinkAudio();
            if (videoEnabled && audioEnabled) {
                return new MultiplexedFrameSource(
                        new FfmpegCameraFrameSource(config.capture()),
                        new FfmpegAudioFrameSource(config.capture()));
            }
            if (audioEnabled) {
                return new FfmpegAudioFrameSource(config.capture());
            }
            if (videoEnabled) {
                return new FfmpegCameraFrameSource(config.capture());
            }
            return new SyntheticFrameSource(
                    request.preferredFrameType() == Jt1078FrameType.AUDIO ? 160 : config.payloadBytesPerPacket(),
                    request.preferredFrameType());
        }
        if ("file".equalsIgnoreCase(config.streamMode())) {
            if (request.uplinkVideo() && request.uplinkAudio()) {
                return new MultiplexedFrameSource(
                        new FileFrameSource(config.mediaFiles(), config.payloadBytesPerPacket(), Jt1078FrameType.VIDEO_P),
                        new SyntheticFrameSource(160, Jt1078FrameType.AUDIO));
            }
            if (request.preferredFrameType() == Jt1078FrameType.PASSTHROUGH) {
                return new Jt1078PassThroughFrameSource(config.payloadBytesPerPacket());
            }
            return new FileFrameSource(config.mediaFiles(), config.payloadBytesPerPacket(), request.preferredFrameType());
        }
        if (request.uplinkVideo() && request.uplinkAudio()) {
            return new MultiplexedFrameSource(
                    new SyntheticFrameSource(config.payloadBytesPerPacket(), Jt1078FrameType.VIDEO_P),
                    new SyntheticFrameSource(160, Jt1078FrameType.AUDIO));
        }
        if (request.preferredFrameType() == Jt1078FrameType.PASSTHROUGH) {
            return new Jt1078PassThroughFrameSource(config.payloadBytesPerPacket());
        }
        return new SyntheticFrameSource(config.payloadBytesPerPacket(), request.preferredFrameType());
    }

    private static int bcd(byte value) {
        return ((value >> 4) & 0x0F) * 10 + (value & 0x0F);
    }

    private static Instant decodeTimestamp(byte[] bcdTimestamp) {
        try {
            int year = 2000 + bcd(bcdTimestamp[0]);
            int month = bcd(bcdTimestamp[1]);
            int day = bcd(bcdTimestamp[2]);
            int hour = bcd(bcdTimestamp[3]);
            int minute = bcd(bcdTimestamp[4]);
            int second = bcd(bcdTimestamp[5]);
            return LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeException ignored) {
            return null;
        }
    }
}
