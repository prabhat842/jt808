package com.example.jt808sim.jt1078;

import com.example.jt808sim.monitoring.MetricsRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Jt1078DownlinkAudioHandler extends SimpleChannelInboundHandler<Jt1078InboundPacket> {
    private static final Logger log = LoggerFactory.getLogger(Jt1078DownlinkAudioHandler.class);

    private final MetricsRegistry metrics;
    private final String terminalId;
    private final int channelId;
    private final DownlinkAudioSink sink;

    public Jt1078DownlinkAudioHandler(MetricsRegistry metrics, String terminalId, int channelId, Jt1078MediaConfig config) {
        this(metrics, terminalId, channelId, createSink(terminalId, channelId, config));
    }

    Jt1078DownlinkAudioHandler(MetricsRegistry metrics, String terminalId, int channelId, DownlinkAudioSink sink) {
        this.metrics = metrics;
        this.terminalId = terminalId;
        this.channelId = channelId;
        this.sink = sink;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Jt1078InboundPacket packet) throws Exception {
        metrics.mediaInboundPackets().increment();
        metrics.mediaInboundBytes().add(packet.payload().length);
        if (packet.frameType() != Jt1078FrameType.AUDIO) {
            return;
        }
        metrics.mediaInboundAudioPackets().increment();
        metrics.mediaInboundAudioBytes().add(packet.payload().length);
        if (sink != null) {
            sink.write(packet.payload());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("JT1078 downlink handler error for {} channel {}", terminalId, channelId, cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        closeSink();
        super.channelInactive(ctx);
    }

    private void closeSink() throws IOException {
        if (sink != null) {
            sink.close();
        }
    }

    private static DownlinkAudioSink createSink(String terminalId, int channelId, Jt1078MediaConfig config) {
        List<DownlinkAudioSink> sinks = new ArrayList<>();
        if (config.talk().recordReceivedAudio()) {
            sinks.add(new FileDownlinkAudioSink(terminalId, channelId, config.talk().recordOutputDirectory()));
        }
        if (config.talk().playReceivedAudio()) {
            sinks.add(new FfmpegPlaybackAudioSink(config.capture()));
        }
        if (sinks.isEmpty()) {
            return null;
        }
        return sinks.size() == 1 ? sinks.getFirst() : new CompositeDownlinkAudioSink(sinks);
    }
}
