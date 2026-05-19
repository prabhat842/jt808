package com.example.jt808sim.jt1078;

import com.example.jt808sim.monitoring.MetricsRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jt1078DownlinkAudioHandlerTest {
    @Test
    void writesAudioPayloadToConfiguredSink() {
        MetricsRegistry metrics = new MetricsRegistry();
        CapturingSink sink = new CapturingSink();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt1078DownlinkAudioHandler(metrics, "00000000000000000001", 1, sink));
        byte[] payload = new byte[] {1, 2, 3};

        channel.writeInbound(new Jt1078InboundPacket(1, "000000000001", 1, Jt1078FrameType.AUDIO, 0, 20, payload));
        channel.close();

        assertEquals(1, metrics.mediaInboundPackets().sum());
        assertEquals(1, metrics.mediaInboundAudioPackets().sum());
        assertEquals(1, sink.frames.size());
        assertArrayEquals(payload, sink.frames.getFirst());
        assertTrue(sink.closed);
    }

    @Test
    void ignoresNonAudioPayloadForSinkWrites() {
        MetricsRegistry metrics = new MetricsRegistry();
        CapturingSink sink = new CapturingSink();
        EmbeddedChannel channel = new EmbeddedChannel(new Jt1078DownlinkAudioHandler(metrics, "00000000000000000001", 1, sink));

        channel.writeInbound(new Jt1078InboundPacket(1, "000000000001", 1, Jt1078FrameType.VIDEO_P, 0, 40, new byte[] {9}));

        assertEquals(1, metrics.mediaInboundPackets().sum());
        assertEquals(0, metrics.mediaInboundAudioPackets().sum());
        assertEquals(0, sink.frames.size());
    }

    private static final class CapturingSink implements DownlinkAudioSink {
        private final List<byte[]> frames = new ArrayList<>();
        private boolean closed;

        @Override
        public void write(byte[] payload) {
            frames.add(payload.clone());
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }
}
