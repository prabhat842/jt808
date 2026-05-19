package com.example.jt808sim.jt1078;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FfmpegAudioFrameSource implements Jt1078FrameSource {
    private static final Logger log = LoggerFactory.getLogger(FfmpegAudioFrameSource.class);
    private static final int MAX_BUFFERED_AUDIO_FRAMES = 64;

    private final Jt1078MediaConfig.CaptureConfig capture;
    private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(MAX_BUFFERED_AUDIO_FRAMES);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final SyntheticFrameSource fallback;
    private volatile Process process;
    private volatile Thread readerThread;
    private volatile Thread stderrThread;

    public FfmpegAudioFrameSource(Jt1078MediaConfig.CaptureConfig capture) {
        this.capture = capture;
        this.fallback = new SyntheticFrameSource(160, Jt1078FrameType.AUDIO);
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        ensureStarted();
        byte[] frame = pollFrame();
        if (frame == null || frame.length == 0) {
            return fallback.nextFrame(frameIndex);
        }
        return new Jt1078Frame(Jt1078FrameType.AUDIO, frameIndex * 20, false, 0, 0, frame);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Thread thread = readerThread;
        if (thread != null) {
            thread.interrupt();
        }
        Thread errorThread = stderrThread;
        if (errorThread != null) {
            errorThread.interrupt();
        }
        Process ffmpeg = process;
        if (ffmpeg != null) {
            ffmpeg.destroy();
        }
    }

    private void ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(buildCommand());
        try {
            process = builder.start();
            readerThread = new Thread(this::readLoop, "ffmpeg-audio-source");
            readerThread.setDaemon(true);
            readerThread.start();
            stderrThread = new Thread(this::readStderrLoop, "ffmpeg-audio-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();
            log.info("started ffmpeg audio capture backend");
        } catch (IOException e) {
            log.warn("failed to start ffmpeg audio capture backend", e);
        }
    }

    private void readLoop() {
        Process ffmpeg = process;
        if (ffmpeg == null) {
            return;
        }
        AdtsFrameParser parser = new AdtsFrameParser();
        byte[] chunk = new byte[8192];
        try (InputStream in = ffmpeg.getInputStream()) {
            while (!closed.get()) {
                int read = in.read(chunk);
                if (read < 0) {
                    break;
                }
                for (byte[] frame : parser.append(chunk, read)) {
                    offerLatest(frame);
                }
            }
            for (byte[] frame : parser.finish()) {
                offerLatest(frame);
            }
        } catch (IOException e) {
            if (!closed.get()) {
                log.warn("ffmpeg audio capture read loop failed", e);
            }
        } finally {
            if (ffmpeg.isAlive()) {
                ffmpeg.destroy();
            }
        }
    }

    private void readStderrLoop() {
        Process ffmpeg = process;
        if (ffmpeg == null) {
            return;
        }
        byte[] chunk = new byte[2048];
        try (InputStream err = ffmpeg.getErrorStream()) {
            while (!closed.get()) {
                int read = err.read(chunk);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    log.debug("ffmpeg: {}", new String(chunk, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim());
                }
            }
        } catch (IOException e) {
            if (!closed.get()) {
                log.debug("ffmpeg stderr read loop failed", e);
            }
        }
    }

    private void offerLatest(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return;
        }
        while (!frames.offer(frame)) {
            frames.poll();
        }
    }

    private byte[] pollFrame() {
        try {
            return frames.poll(5, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private List<String> buildCommand() {
        return buildCommand(capture);
    }

    static List<String> buildCommand(Jt1078MediaConfig.CaptureConfig capture) {
        String audioDevice = capture.audioDevice();
        if (audioDevice != null && audioDevice.startsWith("lavfi:")) {
            return List.of(
                    capture.ffmpegPath(),
                    "-hide_banner",
                    "-loglevel", "warning",
                    "-f", "lavfi",
                    "-i", audioDevice.substring("lavfi:".length()),
                    "-vn",
                    "-ac", Integer.toString(Math.max(1, capture.audioChannels())),
                    "-ar", Integer.toString(Math.max(8000, capture.audioSampleRate())),
                    "-c:a", "aac",
                    "-b:a", Math.max(16, capture.audioBitrateKbps()) + "k",
                    "-f", "adts",
                    "-");
        }
        return List.of(
                capture.ffmpegPath(),
                "-hide_banner",
                "-loglevel", "warning",
                "-f", "alsa",
                "-i", capture.audioDevice(),
                "-vn",
                "-ac", Integer.toString(Math.max(1, capture.audioChannels())),
                "-ar", Integer.toString(Math.max(8000, capture.audioSampleRate())),
                "-c:a", "aac",
                "-b:a", Math.max(16, capture.audioBitrateKbps()) + "k",
                "-f", "adts",
                "-");
    }
}
