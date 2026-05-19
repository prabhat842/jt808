package com.example.jt808sim.jt1078;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class FfmpegPlaybackAudioSink implements DownlinkAudioSink {
    private static final Logger log = LoggerFactory.getLogger(FfmpegPlaybackAudioSink.class);

    private final Jt1078MediaConfig.CaptureConfig capture;
    private Process process;
    private OutputStream stdin;

    public FfmpegPlaybackAudioSink(Jt1078MediaConfig.CaptureConfig capture) {
        this.capture = capture;
    }

    @Override
    public synchronized void write(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return;
        }
        ensureStarted();
        if (stdin != null) {
            stdin.write(payload);
            stdin.flush();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (stdin != null) {
            stdin.close();
            stdin = null;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void ensureStarted() throws IOException {
        if (stdin != null) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(buildCommand());
        process = builder.start();
        stdin = process.getOutputStream();
        log.info("started ffmpeg playback audio sink");
    }

    private List<String> buildCommand() {
        return List.of(
                capture.ffmpegPath(),
                "-f", "aac",
                "-i", "pipe:0",
                "-f", "alsa",
                capture.audioDevice());
    }
}
