package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplexedFrameSourceTest {
    @Test
    void emitsAudioEveryTickAndVideoEveryOtherTick() {
        TrackingSource video = new TrackingSource(Jt1078FrameType.VIDEO_P);
        TrackingSource audio = new TrackingSource(Jt1078FrameType.AUDIO);
        MultiplexedFrameSource source = new MultiplexedFrameSource(video, audio);

        List<Jt1078FrameType> emitted = new ArrayList<>();
        for (long tick = 1; tick <= 4; tick++) {
            for (Jt1078Frame frame : source.nextFrames(tick)) {
                emitted.add(frame.type());
            }
        }

        assertEquals(List.of(
                Jt1078FrameType.AUDIO,
                Jt1078FrameType.AUDIO, Jt1078FrameType.VIDEO_P,
                Jt1078FrameType.AUDIO,
                Jt1078FrameType.AUDIO, Jt1078FrameType.VIDEO_P), emitted);
    }

    @Test
    void closesBothChildren() {
        TrackingSource video = new TrackingSource(Jt1078FrameType.VIDEO_P);
        TrackingSource audio = new TrackingSource(Jt1078FrameType.AUDIO);
        MultiplexedFrameSource source = new MultiplexedFrameSource(video, audio);

        source.close();

        assertTrue(video.closed);
        assertTrue(audio.closed);
    }

    private static final class TrackingSource implements Jt1078FrameSource {
        private final Jt1078FrameType type;
        private boolean closed;

        private TrackingSource(Jt1078FrameType type) {
            this.type = type;
        }

        @Override
        public Jt1078Frame nextFrame(long frameIndex) {
            return new Jt1078Frame(type, frameIndex * (type.isVideo() ? 40 : 20), false, 0, type.isVideo() ? 40 : 0, new byte[] {(byte) frameIndex});
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
