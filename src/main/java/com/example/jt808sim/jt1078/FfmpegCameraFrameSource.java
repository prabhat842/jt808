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

public class FfmpegCameraFrameSource implements Jt1078FrameSource {
    private static final Logger log = LoggerFactory.getLogger(FfmpegCameraFrameSource.class);
    private static final int MAX_BUFFERED_ACCESS_UNITS = 32;

    private final Jt1078MediaConfig.CaptureConfig capture;
    private final BlockingQueue<byte[]> accessUnits = new LinkedBlockingQueue<>(MAX_BUFFERED_ACCESS_UNITS);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final SyntheticFrameSource fallback;
    private volatile Process process;
    private volatile Thread readerThread;
    private volatile Thread stderrThread;

    public FfmpegCameraFrameSource(Jt1078MediaConfig.CaptureConfig capture) {
        this.capture = capture;
        this.fallback = new SyntheticFrameSource(950, Jt1078FrameType.VIDEO_P);
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        ensureStarted();
        byte[] accessUnit = pollAccessUnit();
        if (accessUnit == null || accessUnit.length == 0) {
            return fallback.nextFrame(frameIndex);
        }
        boolean keyFrame = isKeyFrame(accessUnit);
        return new Jt1078Frame(
                keyFrame ? Jt1078FrameType.VIDEO_I : Jt1078FrameType.VIDEO_P,
                frameIndex * 40,
                keyFrame,
                0,
                40,
                accessUnit);
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
        List<String> command = buildCommand();
        ProcessBuilder builder = new ProcessBuilder(command);
        try {
            process = builder.start();
            readerThread = new Thread(this::readLoop, "ffmpeg-camera-source");
            readerThread.setDaemon(true);
            readerThread.start();
            stderrThread = new Thread(this::readStderrLoop, "ffmpeg-camera-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();
            log.info("started ffmpeg camera capture backend");
        } catch (IOException e) {
            log.warn("failed to start ffmpeg camera capture backend", e);
        }
    }

    private void readLoop() {
        Process ffmpeg = process;
        if (ffmpeg == null) {
            return;
        }
        AnnexBAccessUnitParser parser = new AnnexBAccessUnitParser();
        byte[] chunk = new byte[8192];
        try (InputStream in = ffmpeg.getInputStream()) {
            while (!closed.get()) {
                int read = in.read(chunk);
                if (read < 0) {
                    break;
                }
                for (byte[] accessUnit : parser.append(chunk, read)) {
                    offerLatest(accessUnit);
                }
            }
            for (byte[] accessUnit : parser.finish()) {
                offerLatest(accessUnit);
            }
        } catch (IOException e) {
            if (!closed.get()) {
                log.warn("ffmpeg camera capture read loop failed", e);
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

    private void offerLatest(byte[] accessUnit) {
        if (accessUnit == null || accessUnit.length == 0) {
            return;
        }
        while (!accessUnits.offer(accessUnit)) {
            accessUnits.poll();
        }
    }

    private byte[] pollAccessUnit() {
        try {
            return accessUnits.poll(5, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private List<String> buildCommand() {
        return buildCommand(capture);
    }

    static List<String> buildCommand(Jt1078MediaConfig.CaptureConfig capture) {
        String videoDevice = capture.videoDevice();
        String bitrateK    = String.valueOf(Math.max(128, capture.videoBitrateKbps()));
        String gop         = String.valueOf(Math.max(1, capture.videoFps() * 2));

        if (videoDevice != null && videoDevice.startsWith("lavfi:")) {
            return List.of(
                    capture.ffmpegPath(), "-hide_banner", "-loglevel", "warning",
                    "-f", "lavfi", "-i", videoDevice.substring("lavfi:".length()),
                    "-an", "-c:v", "libx264", "-preset", "veryfast",
                    "-tune", "zerolatency", "-pix_fmt", "yuv420p",
                    "-b:v", bitrateK + "k", "-g", gop, "-keyint_min", gop,
                    "-f", "h264", "-");
        }
        if (videoDevice != null && (videoDevice.startsWith("http://") || videoDevice.startsWith("rtsp://"))) {
            // Read from MJPEG HTTP stream (e.g. DMS sidecar /video endpoint)
            return List.of(
                    capture.ffmpegPath(), "-hide_banner", "-loglevel", "warning",
                    "-i", videoDevice,
                    "-an", "-c:v", "libx264", "-preset", "veryfast",
                    "-tune", "zerolatency", "-pix_fmt", "yuv420p",
                    "-b:v", bitrateK + "k", "-g", gop, "-keyint_min", gop,
                    "-f", "h264", "-");
        }
        return List.of(
                capture.ffmpegPath(), "-hide_banner", "-loglevel", "warning",
                "-f", "v4l2",
                "-framerate", String.valueOf(Math.max(1, capture.videoFps())),
                "-video_size", capture.videoWidth() + "x" + capture.videoHeight(),
                "-i", videoDevice,
                "-an", "-c:v", "libx264", "-preset", "veryfast",
                "-tune", "zerolatency", "-pix_fmt", "yuv420p",
                "-b:v", bitrateK + "k", "-g", gop, "-keyint_min", gop,
                "-f", "h264", "-");
    }

    static boolean isKeyFrame(byte[] accessUnit) {
        for (int i = 0; i < accessUnit.length - 4; i++) {
            if (accessUnit[i] == 0 && accessUnit[i + 1] == 0
                    && ((accessUnit[i + 2] == 1) || (accessUnit[i + 2] == 0 && accessUnit[i + 3] == 1))) {
                int nalIndex = accessUnit[i + 2] == 1 ? i + 3 : i + 4;
                if (nalIndex < accessUnit.length) {
                    int nalType = accessUnit[nalIndex] & 0x1F;
                    if (nalType == 5) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
