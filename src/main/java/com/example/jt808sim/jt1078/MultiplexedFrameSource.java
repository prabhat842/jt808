package com.example.jt808sim.jt1078;

import java.util.ArrayList;
import java.util.List;

public class MultiplexedFrameSource implements Jt1078FrameSource {
    private static final int AUDIO_INTERVAL_MILLIS = 20;
    private static final int VIDEO_INTERVAL_MILLIS = 40;

    private final Jt1078FrameSource videoSource;
    private final Jt1078FrameSource audioSource;
    private long nextVideoMillis = VIDEO_INTERVAL_MILLIS;
    private long nextAudioMillis = AUDIO_INTERVAL_MILLIS;
    private long videoIndex;
    private long audioIndex;

    public MultiplexedFrameSource(Jt1078FrameSource videoSource, Jt1078FrameSource audioSource) {
        this.videoSource = videoSource;
        this.audioSource = audioSource;
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        List<Jt1078Frame> frames = nextFrames(frameIndex);
        return frames.isEmpty() ? null : frames.getFirst();
    }

    @Override
    public List<Jt1078Frame> nextFrames(long tickIndex) {
        long currentMillis = tickIndex * AUDIO_INTERVAL_MILLIS;
        List<Jt1078Frame> frames = new ArrayList<>(2);
        if (audioSource != null && currentMillis >= nextAudioMillis) {
            Jt1078Frame frame = audioSource.nextFrame(++audioIndex);
            if (frame != null) {
                frames.add(frame);
            }
            nextAudioMillis += AUDIO_INTERVAL_MILLIS;
        }
        if (videoSource != null && currentMillis >= nextVideoMillis) {
            Jt1078Frame frame = videoSource.nextFrame(++videoIndex);
            if (frame != null) {
                frames.add(frame);
            }
            nextVideoMillis += VIDEO_INTERVAL_MILLIS;
        }
        return frames;
    }

    @Override
    public void close() {
        if (videoSource != null) {
            videoSource.close();
        }
        if (audioSource != null) {
            audioSource.close();
        }
    }
}
