package com.example.jt808sim.jt1078;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FileFrameSource implements Jt1078FrameSource {
    private static final Logger log = LoggerFactory.getLogger(FileFrameSource.class);

    private final byte[] data;
    private final int payloadBytes;
    private final Jt1078FrameType frameType;
    private int offset;

    public FileFrameSource(List<String> mediaFiles, int payloadBytes, Jt1078FrameType frameType) {
        this.data = load(mediaFiles);
        this.payloadBytes = payloadBytes;
        this.frameType = frameType;
    }

    @Override
    public synchronized Jt1078Frame nextFrame(long frameIndex) {
        byte[] payload = new byte[payloadBytes];
        if (data.length == 0) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) ((frameIndex + i) & 0xFF);
            }
        } else {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = data[offset];
                offset = (offset + 1) % data.length;
            }
        }
        boolean video = frameType.isVideo();
        long timestampMillis = video ? frameIndex * 40 : frameIndex * 20;
        return new Jt1078Frame(frameType, timestampMillis, frameType == Jt1078FrameType.VIDEO_I, 0, video ? 40 : 0, payload);
    }

    private static byte[] load(List<String> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            log.warn("jt1078.streamMode=file selected but no mediaFiles configured; using synthetic payloads");
            return new byte[0];
        }
        String file = mediaFiles.get(ThreadLocalRandom.current().nextInt(mediaFiles.size()));
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file));
            if (bytes.length == 0) {
                log.warn("media file {} is empty; using synthetic payloads", file);
            } else {
                log.info("streaming JT1078 payloads from {}", file);
            }
            return bytes;
        } catch (IOException e) {
            log.warn("failed to read media file {}; using synthetic payloads", file, e);
            return new byte[0];
        }
    }
}
